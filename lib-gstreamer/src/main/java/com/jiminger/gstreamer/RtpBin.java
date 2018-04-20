package com.jiminger.gstreamer;

import java.util.ArrayList;
import java.util.List;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pad;

public class RtpBin {
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

    private static class ElementPad {
        public final Element element;
        public final Pad pad;

        // public ElementPad(final Element element, final Pad pad) {
        // this.element = element;
        // this.pad = pad;
        // }

        public ElementPad(final Element element, final boolean sink) {
            this.element = element;
            final List<Pad> pads = sink ? element.getSinkPads() : element.getSrcPads();
            if (pads == null || pads.size() == 0 || pads.size() > 1)
                throw new IllegalStateException(
                        "The element \"" + element + "\" doesn't have the right number of pads: " + (pads == null ? 0 : pads.size()));
            this.pad = pads.get(0);
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

    public RtpBin sendSession(final int sessionId) {
        currentSession = new SessionConfig(true, sessionId);
        sessions.add(currentSession);
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
        final SessionConfig ses = sessions.stream().filter(s -> s.sessionId == sessionId).findFirst().orElse(null);
        if (ses == null)
            throw new IllegalArgumentException("RtpBin " + name + " has no session with an id of " + sessionId);
        return rtpBin.getRequestPad(ses.padName(true, true));
    }

    public Bin build(final Bin pipe) {
        pipe.add(rtpBin);
        sessions.stream().forEach(s -> s.addTo(pipe));
        sessions.stream().forEach(s -> {
            // incommingRtp means
            if (s.incommingRtp != null)
                Element.linkPads(s.incommingRtp.element, s.incommingRtp.pad.getName(), rtpBin, s.padName(false, true));

            if (s.outgoingRtp != null)
                Element.linkPads(rtpBin, s.padName(false, false), s.outgoingRtp.element, s.outgoingRtp.pad.getName());

            if (s.sendRtcp != null)
                Element.linkPads(rtpBin, s.padName(true, false), s.sendRtcp.element, s.sendRtcp.pad.getName());

            if (s.recvRtcp != null)
                Element.linkPads(s.recvRtcp.element, s.recvRtcp.pad.getName(), rtpBin, s.padName(true, true));
        });

        return rtpBin;
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
}
