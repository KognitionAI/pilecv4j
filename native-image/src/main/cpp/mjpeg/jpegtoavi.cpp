/**
 * This code is largely a copy of jpegtoavi.c. The original can be
 * found here: https://sourceforge.net/projects/jpegtoavi/files/jpegtoavi/
 *
 * The license from the original code is afixed below. This is different
 * from the license the rest of this project is under.
 */
/*
  jpegtoavi

  A simple converter of JPEGs to an AVI/MJPEG animation.

  Copyright (C) 2003 Phillip Bruce <dI77IHd@yahoo.com>

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.
  
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  
  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

#define _LARGEFILE_SOURCE
#define _LARGEFILE64_SOURCE

#include <stdio.h>
#include "avifmt.h"
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#if (defined _BCC || defined _WINDOWS)
 #ifdef _BCC
  #include <climits.h>
 #else
  #include <limits.h>
 #endif  
 #include <io.h>
 typedef __int64 off64_t;
#else
  #include <unistd.h>
#endif
#include <fcntl.h>
#include "byteswap.h"
#include <stdlib.h>

#include <list>

#include "common/kog_exports.h"
using namespace std;

/*
  spc: printing 4 byte word in little-endian fmt
*/
static void print_quartet(unsigned int i,FILE* ofd)
{
  putc(i%0x100,ofd); i/=0x100;
  putc(i%0x100,ofd); i/=0x100;
  putc(i%0x100,ofd); i/=0x100;
  putc(i%0x100,ofd);
}

static bool writeHeader(DWORD per_usec, long jpg_sz, DWORD frames,
                        DWORD width, DWORD height, DWORD riff_sz,
                        FILE* ofd)
{
  struct AVI_list_hdrl hdrl={
    /* header */
    {
      {'L','I','S','T'},
      LILEND4(sizeof(struct AVI_list_hdrl)-8),
      {'h','d','r','l'}
    },

    /* chunk avih */
    {'a','v','i','h'},
    LILEND4(sizeof(struct AVI_avih)),
    {
      LILEND4(per_usec),
      (DWORD) LILEND4(1000000*(jpg_sz/frames)/per_usec),
      LILEND4(0),
      LILEND4(AVIF_HASINDEX),
      LILEND4(frames),
      LILEND4(0),
      LILEND4(1),
      LILEND4(0),
      LILEND4(width),
      LILEND4(height),
      {LILEND4(0),LILEND4(0),LILEND4(0),LILEND4(0)}
    },

    /* list strl */
    {
      {
  {'L','I','S','T'},
  LILEND4(sizeof(struct AVI_list_strl)-8),
  {'s','t','r','l'}
      },

      /* chunk strh */
      {'s','t','r','h'},
      LILEND4(sizeof(struct AVI_strh)),
      {
  {'v','i','d','s'},
  {'M','J','P','G'},
  LILEND4(0),
  LILEND4(0),
  LILEND4(0),
  LILEND4(per_usec),
  LILEND4(1000000),
  LILEND4(0),
  LILEND4(frames),
  LILEND4(0),
  LILEND4(0),
  LILEND4(0)
      },

      /* chunk strf */
      {'s','t','r','f'},
      sizeof(struct AVI_strf),
      {
  LILEND4(sizeof(struct AVI_strf)),
  LILEND4(width),
  LILEND4(height),
  LILEND4(1+24*256*256),
  {'M','J','P','G'},
  LILEND4(width*height*3),
  LILEND4(0),
  LILEND4(0),
  LILEND4(0),
  LILEND4(0)
      },

      /* list odml */
      {
  {
    {'L','I','S','T'},
    LILEND4(16),
    {'o','d','m','l'}
  },
  {'d','m','l','h'},
  LILEND4(4),
  LILEND4(frames)
      }
    }
  };

  /* printing AVI.. riff hdr */
  fwrite("RIFF",1,4,ofd);
  print_quartet(riff_sz,ofd);
  fwrite("AVI ",1,4,ofd);

  /* list hdrl */
  hdrl.avih.us_per_frame=LILEND4(per_usec);
  hdrl.avih.max_bytes_per_sec=LILEND4(1000000*(jpg_sz/frames)
              /per_usec);
  hdrl.avih.tot_frames=LILEND4(frames);
  hdrl.avih.width=LILEND4(width);
  hdrl.avih.height=LILEND4(height);
  hdrl.strl.strh.scale=LILEND4(per_usec);
  hdrl.strl.strh.rate=LILEND4(1000000L);
  hdrl.strl.strh.length=LILEND4(frames);
  hdrl.strl.strf.width=LILEND4(width);
  hdrl.strl.strf.height=LILEND4(height);
  hdrl.strl.strf.image_sz=LILEND4(width*height*3);
  hdrl.strl.list_odml.frames=LILEND4(frames);
  fwrite(&hdrl,sizeof(hdrl),1,ofd);

  /* list movi */
  fwrite("LIST",1,4,ofd);
  print_quartet(jpg_sz+8*frames+4,ofd);
  return true;
}


/*
  spc: indicating file sz in bytes, -1 on error
*/
static off_t file_sz(const char *fn)
{
  struct stat s;
  if(stat(fn,&s)==-1)
    return -1;
  return s.st_size;
}

static FILE* ofd = NULL;
static DWORD width = (DWORD)-1;
static DWORD height = (DWORD)-1;
static long f = 0;
static list<DWORD>* offsets;
static list<DWORD>* szarray;
static DWORD prevsz;
static DWORD prevoffset;
static unsigned long tnbw = 0;
static off64_t jpg_sz_64 = 0;

// This is to get rid of some MSVC warnings about fopen being unsafe.
static void open_file(FILE** file, const char* fname, const char* mode) {
#if _MSC_VER
    if (fopen_s(file, fname, mode) != 0)
        *file = nullptr;
#else
    *file = fopen(fname, mode);
#endif
}

extern "C" {
KAI_EXPORT int32_t pilecv4j_image_mjpeg_initializeMJPEG(const char* filename)
{
  DWORD frames=1;
  DWORD width=1;
  DWORD height=1;
//  const off64_t MAX_RIFF_SZ=2147483648; /* 2 Gb limit */
  DWORD riff_sz = 0;
  DWORD fps = 1;

  open_file(&ofd, filename,"wb");

  if (ofd == NULL)
  {
     fprintf(stderr,"Failed to open file %s",filename);
     return false;
  }

  DWORD per_usec=1000000/fps;

  writeHeader(per_usec,1, frames,
              width, height, riff_sz,
              ofd);

  fwrite("movi",1,4,ofd);

  return true;
}

KAI_EXPORT int32_t pilecv4j_image_mjpeg_doappendFile(const char* filename, int32_t jwidth, int32_t jheight)
{
   off_t mfsz,remnant;
   char buff[512];
   long nbr,nbw;

   if (ofd == NULL)
      return false;

   if (height == (DWORD)-1)
   {
      height = jheight;
      width = jwidth;
      f = 0;
      offsets = new list<DWORD>;
      szarray = new list<DWORD>;
      prevsz = (DWORD)-1;
      prevoffset = (DWORD)-1;
      tnbw = 0;
      jpg_sz_64 = 0;
   }

   fwrite("00db",1,4,ofd);
   DWORD cursz = file_sz(filename);
   jpg_sz_64 += (cursz + ((4-(cursz%4))%4));

   if (cursz == (DWORD)-1)
   {
      return false;
   }
   mfsz = cursz; // szarray[f-img0];

   remnant=(4-(mfsz%4))%4;

   print_quartet(mfsz+remnant,ofd);
   cursz += remnant;
   szarray->push_back(cursz);
   DWORD curoffset;
   if(f==0)
   {
      curoffset = 4;
   }
   else
   {
      curoffset = prevoffset+prevsz+8;
   }

   offsets->push_back(curoffset);

   FILE* fd;
   open_file(&fd, filename, "rb");
   if(!fd)
   {
      fprintf(stderr,"couldn't open file!\n");
      return false;
   }

   if((nbr=static_cast<long>(fread(buff,1,6,fd)))!=6)
   {
      fprintf(stderr,"error\n");
      return false;
   }
   fwrite(buff,nbr,1,ofd);
   if (fread(buff,1,4,fd) != 4) {
     return false;
   }
   fwrite("AVI1",4,1,ofd);
   nbw=10;

   while((nbr=static_cast<long>(fread(buff,1,512,fd)))>0)
   {
      fwrite(buff,nbr,1,ofd);
      nbw+=nbr;
   }
   if(remnant>0)
   {
      fwrite(buff,remnant,1,ofd);
      nbw+=remnant;
   }
   tnbw+=nbw;
   fclose(fd);

   prevsz = cursz;
   prevoffset = curoffset;
   f++;

   return true;
}

KAI_EXPORT int32_t pilecv4j_image_mjpeg_close(int32_t jfps)
{
   unsigned int fps = (unsigned int)jfps;
   const off64_t MAX_RIFF_SZ=2147483648UL; /* 2 Gb limit */

   if (ofd == NULL)
      return false;

   unsigned long jpg_sz = (unsigned long)jpg_sz_64;

   if(tnbw!=jpg_sz)
   {
      fprintf(stderr,"error writing images (wrote %ld bytes, expected %ld bytes)\n",
              tnbw,jpg_sz);
      return false;
   }

   // indices
   DWORD frames = (DWORD)offsets->size();
   fwrite("idx1",1,4,ofd);
   print_quartet(16*frames,ofd);
   list<DWORD>::const_iterator iter;
   list<DWORD>::const_iterator sziter;

   sziter = szarray->begin();
   for (iter = offsets->begin(); iter != offsets->end(); iter++)
   {
      DWORD curoffset = (*iter);
      DWORD cursz = (*sziter);
      fwrite("00db",1,4,ofd);
      print_quartet(18,ofd);
      print_quartet(curoffset,ofd);
      print_quartet(cursz,ofd);
      sziter++;
   }

   fseek(ofd,0,SEEK_SET);

   off64_t riff_sz_64=sizeof(struct AVI_list_hdrl)+4+4+jpg_sz_64
      +8*frames+8+8+16*frames;
   if(riff_sz_64>=MAX_RIFF_SZ)
   {
      fprintf(stderr,"RIFF would exceed 2 Gb limit\n");
      return false;
   }
   DWORD riff_sz=(DWORD)riff_sz_64;

   DWORD per_usec=1000000/fps;
   writeHeader(per_usec,jpg_sz, frames,
               width, height, riff_sz,
               ofd);

   fflush(ofd);
   return true;
}

KAI_EXPORT void pilecv4j_image_mjpeg_cleanUp()
{
   if (ofd)
   {
      fflush(ofd);
      fclose(ofd);
   }

   ofd = NULL;
   height = (DWORD)-1;
   width = (DWORD)-1;
   f = 0;
   prevsz = (DWORD)-1;
   prevoffset = (DWORD)-1;
   tnbw = 0;
   jpg_sz_64 = 0;
   if (offsets) delete offsets;
   offsets = NULL;
   if (szarray) delete szarray;
   szarray = NULL;
}
}

