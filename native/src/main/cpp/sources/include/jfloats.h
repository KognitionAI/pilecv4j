/***********************************************************************
    Legacy Film to DVD Project
    Copyright (C) 2005 James F. Carroll

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
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
****************************************************************************/

#ifndef _JFloat__
#define _JFloat__

#if defined(FLOAT_4BYTE)
typedef float float32_t;
#elif defined(DOUBLE_4BYTE)
typedef double float32_t;
#elif defined(LONG_DOUBLE_4BYTE)
typedef long double float32_t;
#else
#error "Unable to deterine which type is a 32 bit float"
#endif

#if defined(FLOAT_8BYTE)
typedef float float64_t;
#elif defined(DOUBLE_8BYTE)
typedef double float64_t;
#elif defined(LONG_DOUBLE_8BYTE)
typedef long double float64_t;
#else
#error "Unable to deterine which type is a 64 bit float"
#endif

#endif




