#ifndef BYTEORDER_H_INCLUDED
#define BYTEORDER_H_INCLUDED

#ifdef __cplusplus
extern "C" {
#endif

#ifdef __cplusplus
}
#endif

void decode16_r(unsigned short *out, const unsigned char *in);
void decode16_rv(unsigned short *out, const unsigned char *in, int count);
void encode16_r(unsigned char *out, const unsigned short *in);
void encode16_rv(unsigned char *out, const unsigned short *in, int count);
void decode32_r(unsigned long *out, const unsigned char *in);
void decode32_rv(unsigned long *out, const unsigned char *in, int count);
void encode32_r(unsigned char *out, const unsigned long *in);
void encode32_rv(unsigned char *out, const unsigned long *in, int count);

#ifdef LSBF

#define decode16msbf decode16_r
#define decode16msbfa decode16_rv
#define encode16msbf encode16_r
#define encode16msbfa encode16_rv
#define decode16lsbf(l, b) memcpy(l, b, sizeof(unsigned short))
#define decode16lsbfa(l, b, c) memcpy(l, b, (c) * sizeof(unsigned short))
#define encode16lsbf(b, l) memcpy(b, l, sizeof(unsigned short))
#define encode16lsbfa(b, l, c) memcpy(b, l, (c) * sizeof(unsigned short))
#define decode32msbf decode32_r
#define decode32msbfa decode32_rv
#define encode32msbf encode32_r
#define encode32msbfa encode32_rv
#define decode32lsbf(l, b) memcpy(l, b, sizeof(unsigned long))
#define decode32lsbfa(l, b, c) memcpy(l, b, (c) * sizeof(unsigned long))
#define encode32lsbf(b, l) memcpy(b, l, sizeof(unsigned long))
#define encode32lsbfa(b, l, c) memcpy(b, l, (c) * sizeof(unsigned long))

#else

#define decode16msbf(l, b) memcpy(l, b, sizeof(unsigned short))
#define decode16msbfa(l, b, c) memcpy(l, b, (c) * sizeof(unsigned short))
#define encode16msbf(b, l) memcpy(b, l, sizeof(unsigned short))
#define encode16msbfa(b, l, c) memcpy(b, l, (c) * sizeof(unsigned short))
#define decode16lsbf decode16_r
#define decode16lsbfa decode16_rv
#define encode16lsbf encode16_r
#define encode16lsbfa encode16_rv
#define decode32msbf(l, b) memcpy(l, b, sizeof(unsigned long))
#define decode32msbfa(l, b, c) memcpy(l, b, (c) * sizeof(unsigned long))
#define encode32msbf(b, l) memcpy(b, l, sizeof(unsigned long))
#define encode32msbfa(b, l, c) memcpy(b, l, (c) * sizeof(unsigned long))
#define decode32lsbf decode32_r
#define decode32lsbfa decode32_rv
#define encode32lsbf encode32_r
#define encode32lsbfa encode32_rv

#endif

#endif
