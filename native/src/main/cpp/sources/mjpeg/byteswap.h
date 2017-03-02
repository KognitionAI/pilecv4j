#ifndef _BYTESWAP_H_
#define _BYTESWAP_H_

#if (defined _BCC || defined MSVC)
#define __LITTLE_ENDIAN 0
#define __BIG_ENDIAN 1
#define __BYTE_ORDER __LITTLE_ENDIAN
#define BIG_ENDIAN __BIG_ENDIAN
#define LITTLE_ENDIAN __LITTLE___ENDIAN
#define BYTE_ORDER __BYTE_ORDER
#define EMPTY_ARRAY 1
#else
#ifdef LINUX
#include <endian.h>
#else
# ifdef _WINDOWS
   /* Pm windows we're going to assum little endian on
      an iX86 arch*/
#  define __BYTE_ORDER __LITTLE_ENDIAN
#  define BYTE_ORDER __BYTE_ORDER
# else
#  error "CAN ONLY BUILD FOR LINUX OR _WINDOWS"
# endif
#endif
#endif
#include <sys/types.h>

#ifndef __USE_BSD
//# define BYTE_ORDER     __BYTE_ORDER
//# define off64_t _off64_t
#endif

#ifndef BYTE_ORDER
# error "Aiee: BYTE_ORDER not defined\n";
#endif

#define SWAP2(x) (((x>>8) & 0x00ff) |\
                  ((x<<8) & 0xff00))

#define SWAP4(x) (((x>>24) & 0x000000ff) |\
                  ((x>>8)  & 0x0000ff00) |\
                  ((x<<8)  & 0x00ff0000) |\
                  ((x<<24) & 0xff000000))

#if BYTE_ORDER==BIG_ENDIAN
# define LILEND2(a) SWAP2((a))
# define LILEND4(a) SWAP4((a))
# define BIGEND2(a) (a)
# define BIGEND4(a) (a)
#else
# define LILEND2(a) (a)
# define LILEND4(a) (a)
# define BIGEND2(a) SWAP2((a))
# define BIGEND4(a) SWAP4((a))
#endif

#endif
