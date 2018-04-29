package com.jiminger.gstreamer;

import java.util.ArrayList;
import java.util.List;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtpBin {
    private static final Logger LOGGER = LoggerFactory.getLogger(RtpBin.class);
    private static final String NO_SESSION = "You must create a session first using sendSession/recvSession";
    private static final String EXISTING_EP = "You already set branch for %s for session %d";

    private static final String RTPTO = "rtp outgoing";
    private static final String RTPFROM = "rtp incoming";
    private static final String RTCPSEND = "sending rtpc";
    private static final String RTCPRECV = "receiving rtpc";

    private SessionConfig currentSession = null;
    private final List<SessionConfig> sessions = new ArrayList<>();

    public final String name;

    private final Bin rtpBin;

    public RtpBin(final String name) {
        this.name = name == null ? ElementBuilder.nextName("rtpbin") : name;
        rtpBin = (Bin) ElementFactory.make("rtpbin", this.name);
    }

    public RtpBin() {
        this(null);
    }

    public RtpBin sendSession(final int sessionId) {
        if (sessions.stream().filter(s -> s.sessionId == sessionId).findFirst().orElse(null) != null) {
            final SessionConfig cur = currentSession;
            switchSession(sessionId);
            if (!currentSession.sending) {
                currentSession = cur; // put it back.
                throw new IllegalArgumentException(
                        "Can't swtich to send session " + sessionId + " for RtpBin " + rtpBin + " because it's not a sending session.");
            }
            return this;
        }

        currentSession = new SessionConfig(true, sessionId);
        sessions.add(currentSession);
        return this;
    }

    public RtpBin switchSession(final int sessionId) {
        currentSession = sessions.stream().filter(s -> s.sessionId == sessionId).findFirst().orElse(null);
        if (currentSession == null)
            throw new IllegalArgumentException("Can't switch RtpBin " + rtpBin + " to session " + sessionId + " because it doesn't exist yet.");
        return this;
    }

    public RtpBin rtpTo(final Element sink) {
        checkNull(checkInSession().outgoingRtp, RTPTO).outgoingRtp = new ElementPad(sink, true);
        return this;
    }

    public RtpBin rtpFrom(final Element src) {
        checkNull(checkInSession().incommingRtp, RTPFROM).incommingRtp = new ElementPad(src, false);
        return this;
    }

    public RtpBin rtcpSend(final Element sink) {
        checkNull(checkInSession().sendRtcp, RTCPSEND).sendRtcp = new ElementPad(sink, true);
        return this;
    }

    public RtpBin rtcpRecv(final Element src) {
        checkNull(checkInSession().recvRtcp, RTCPRECV).recvRtcp = new ElementPad(src, false);
        return this;
    }

    public Pad getRtcpRecvPad(final int sessionId) {
        return getRequestPad(sessionId, true, true);
    }

    public Pad getRtpFromPad(final int sessionId) {
        return getRequestPad(sessionId, false, true);
    }

    private Pad getRequestPad(final int sessionId, final boolean rtcp, final boolean sink) {
        final SessionConfig ses = sessions.stream().filter(s -> s.sessionId == sessionId).findFirst().orElse(null);
        if (ses == null)
            throw new IllegalArgumentException("RtpBin " + name + " has no session with an id of " + sessionId);
        // let's see if there's already
        final String padName = ses.padName(rtcp, sink);
        final List<Pad> pads = sink ? rtpBin.getSinkPads() : rtpBin.getSinkPads();
        final Pad ret = pads.stream().filter(p -> padName.equals(p.getName())).findFirst().orElse(null);
        return ret == null ? rtpBin.getRequestPad(padName) : ret;
    }

    public RtpBin addTo(final Bin pipe) {
        pipe.add(rtpBin);
        sessions.stream().forEach(s -> s.addTo(pipe));

        return this;
    }

    public RtpBin linkAll() {

        sessions.stream().forEach(s -> {
            // incommingRtp means
            if (s.incommingRtp != null)
                checkLink(s.incommingRtp.element, s.incommingRtp.pad().getName(), rtpBin, s.padName(false, true));
            else {
                final Pad pad = rtpBin.getRequestPad(s.padName(false, true)); // this sets up the session even if there isn't a source right now.
                if (pad == null)
                    throw new IllegalStateException("Failed to get request pad " + s.padName(false, true));
            }

            if (s.outgoingRtp != null)
                checkLink(rtpBin, s.padName(false, false), s.outgoingRtp.element, s.outgoingRtp.pad().getName());

            if (s.recvRtcp != null)
                checkLink(s.recvRtcp.element, s.recvRtcp.pad().getName(), rtpBin, s.padName(true, true));
            else {
                final Pad pad = rtpBin.getRequestPad(s.padName(true, true));
                if (pad == null)
                    throw new IllegalStateException("Failed to get request pad " + s.padName(true, true));
            }

            if (s.sendRtcp != null)
                checkLink(rtpBin, s.padName(true, false), s.sendRtcp.element, s.sendRtcp.pad().getName());

        });

        return this;
    }

    private void checkLink(final Element src, final String srcPad, final Element sink, final String sinkPad) {
        if (!Element.linkPads(src, srcPad, sink, sinkPad)) {
            LOGGER.error("Link failed from src element " + src + " pad " + srcPad + " to sink element " + sink + " pad " + sinkPad);
            throw new RuntimeException("Link failed from src element " + src + " pad " + srcPad + " to sink element " + sink + " pad " + sinkPad);
        }
    }

    private SessionConfig checkInSession() {
        if (currentSession == null)
            throw new NullPointerException(NO_SESSION);
        return currentSession;
    }

    private SessionConfig checkNull(final ElementPad s, final String dirDesc) {
        if (s != null)
            throw new IllegalStateException(String.format(EXISTING_EP, dirDesc, currentSession.sessionId));
        return currentSession;
    }

    private static class ElementPad {
        public final Element element;
        public final boolean sink;

        public ElementPad(final Element element, final boolean sink) {
            this.element = element;
            this.sink = sink;
        }

        public Pad pad() {
            final List<Pad> pads = sink ? element.getSinkPads() : element.getSrcPads();
            if (pads == null || pads.size() == 0 || pads.size() > 1)
                throw new IllegalStateException(
                        "The element \"" + element + "\" doesn't have the right number of pads: " + (pads == null ? 0 : pads.size()));
            return pads.get(0);
        }
    }

    private static class SessionConfig {
        public final boolean sending;
        public final int sessionId;

        private ElementPad incommingRtp = null;
        private ElementPad outgoingRtp = null;
        private ElementPad recvRtcp = null;
        private ElementPad sendRtcp = null;

        private SessionConfig(final boolean sending, final int sessionId) {
            this.sending = sending;
            this.sessionId = sessionId;
        }

        private void addTo(final Bin pipe) {
            if (incommingRtp != null)
                pipe.add(incommingRtp.element);

            if (outgoingRtp != null)
                pipe.add(outgoingRtp.element);

            if (recvRtcp != null)
                pipe.add(recvRtcp.element);

            if (sendRtcp != null)
                pipe.add(sendRtcp.element);
        }

        public String padName(final boolean rtcp, final boolean sink) {
            if (rtcp)
                return (sink ? "recv_" : "send_") + "rtcp_" + (sink ? "sink_" : "src_") + sessionId;
            else
                return (sending ? "send_" : "recv_") + "rtp_" + (sink ? "sink_" : "src_") + sessionId;
        }
    }
}
