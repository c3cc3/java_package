#ifndef CONV_DATA_H_
#define CONV_DATA_H_

int conv_dec_data(char* src, char* dst, int len);
int conv_enc_data(char *src, char *dst, int nSize);
int des_decrypt_file( char *src_path_file, char **ptr_errmsg );
int des_encrypt_file( char *src_path_file, char **ptr_errmsg );
	
#endif
