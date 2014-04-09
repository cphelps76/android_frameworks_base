/** @file system_key_server.c
 *  @par Copyright:
 *  - Copyright 2013 Amlogic Inc as unpublished work                             
 *  All Rights Reserved                                                                                                                              
 *  - The information contained herein is the confidential property            
 *  of Amlogic.  The use, copying, transfer or disclosure of such information
 *  is prohibited except by express written agreement with Amlogic Inc. 
 *  @author   hu.zhang
 *  @version  1.0        
 *  @date     2013/01/23
 *  @par function description:
 *   - Provide a service to read and write System KEY.
 */

#include "system_key_server.h"
int main(){
    int BUF_SIZE = 1024;
    int s_control = -1;
    int l_state = -1;
    int s_accept = -1;
    int fd_version = -1 ;
    int w_version_num = -1 ;
    int fd_version_check = -1 ;
    int fd_key_value = -1 ;
    int fd_use_data = -1;
    int w_value_num = -1 ;
    int fd_key_name= -1 ;
    int w_name_num = -1 ;
    int fd_key_read= -1 ;
    int key_read_num= -1 ;
    int use_data_info= -1 ;
    int fd1 = -1 ;
    int fsocket = -1 ;
    int fr = -1 ;
    int recv_bytes = -1;
    int send_bytes =-1;
    char recv_buf[BUF_SIZE] ;
    char key_version_buf[BUF_SIZE] ;
    char key_name_buf[BUF_SIZE] ;
    char key_value_buf[BUF_SIZE] ;
    int i = -1;
    int j = -1;
    int n = -1;
    int len_version = -1;
    int len_name = -1;
    int len_value = -1;
    char *default_version = "nand3";
    char *buf_version ;
    char *buf_name ;
    char *buf_value ;
    char *buf_use_data ;
    static int isWrited = 0;
    char* error_info = "#!!#success" ;
    struct sockaddr peeraddr ;
    socklen_t socklen ;
    sem_t bin_sem;
    //sem_post(&bin_sem);
    s_control = android_get_control_socket(SYS_WRITE);
    if (s_control < 0) {
        LOGI("Failed to get socket !");
            exit(-1);
    }
    l_state = listen(s_control, 4);
	if(l_state < 0){
	    LOGI("socket listen error !");
            exit(-1);
        } 
   socklen = sizeof(peeraddr);
	while(1) {
	    s_accept = accept(s_control, &peeraddr, &socklen);
	    if(s_accept < 0){
	    LOGI("socket accept error !");
	    }
	    else{
		memset(recv_buf , '\0', BUF_SIZE);
		if((recv_bytes = recv(s_accept,recv_buf,sizeof(recv_buf),0))==-1)
		{
		    LOGI("recv nothing from socket !");
		}
		else if(recv_bytes >= 0 ){				
		    //the string like :  "w,version,key_name,key_value" or "r,key_name"
		    LOGI("recv the content form socket is : %s , length is : %d" , recv_buf ,recv_bytes);
	            memset(key_version_buf ,'\0', BUF_SIZE);
		    memset(key_name_buf ,'\0', BUF_SIZE);
		    memset(key_value_buf ,'\0', BUF_SIZE);
		    if(recv_buf[0] == 'w'){
			j = 0 ;
			n = 0 ;
			for(i=2;i< recv_bytes ;i++)
			{	
			    if (recv_buf[i]=='\0') 
			    {
				LOGI("to the end !");
				break;
			    }
			    if(recv_buf[i]==',')
			    {	
				n++;
			        j = 0 ;
				continue ;
			    }
			    if(n==0)
			    {
				key_version_buf[j] = recv_buf[i];
				j++;
			    }else if(n == 1)
			    {
				key_name_buf[j] = recv_buf[i];
				j++;
			    }else if(n == 2)
			    {
				key_value_buf[j] = recv_buf[i];
				j++;
			    }else{
				LOGI("some thing wrong in write !!! ");
			    }
			}
		//version 
		len_version = strlen(key_version_buf);
		buf_version = (char *)malloc(len_version);
		memset(buf_version ,'\0', len_version);
		strcpy(buf_version, key_version_buf);
		LOGI(" version is : %s , size is : %d", buf_version, len_version );
		if(strncmp(buf_version,"#####",5)== 0){
			LOGI(" version = 1");
			isWrited = 1;
		}else{
			LOGI(" version = 0");
			isWrited = 0;
		}
					
		//key_name
		len_name= strlen(key_name_buf);
		buf_name = (char *)malloc(len_name);
		memset(buf_name ,'\0', len_name);
		strcpy(buf_name, key_name_buf);
		LOGI(" key name is : %s , size is : %d", buf_name, len_name );
					
		//key_value
		len_value= strlen(key_value_buf);
		buf_value= (char *)malloc(len_value);
		memset(buf_value ,'\0', len_value);
		strcpy(buf_value, key_value_buf);
		LOGI(" key value is : %s , size is : %d", buf_value, len_value );

		//check version 
		fd_version_check = open("/sys/class/aml_keys/aml_keys/key_write", O_WRONLY);
		if( fd_version_check >= 0 )
		{
			isWrited = 1;
		}else{
			isWrited = 0;
		}					
			close(fd_version_check);
					
		//write key version
		if(isWrited == 0)
		{
			isWrited = 1;
			fd_version = open("/sys/class/aml_keys/aml_keys/version", O_WRONLY);
			if( fd_version < 0) 
			{
				error_info = "error : can't open version file!";
				LOGI("%s \n",error_info);
			}else{
				w_version_num = write(fd_version, buf_version , len_version);
				if( w_version_num < 0) 
				{
					error_info = "error : can't write version file!";
					LOGI("%s \n",error_info);
				}
			}
			free(buf_version);
			close(fd_version);
			sleep(3);
		}
		//write key name
		fd_key_name = open("/sys/class/aml_keys/aml_keys/key_name", O_WRONLY);
		if( fd_key_name < 0) 
		{
			error_info = "error : can't open key_name file!";
			LOGI("%s \n",error_info);
		}
					
		w_name_num = write(fd_key_name, buf_name , len_name);
		free(buf_name);
		if( w_name_num < 0) 
		{
			error_info = "error : can't write key_name file!";
			LOGI("%s \n",error_info);
		}
		close(fd_key_name);
		//write key value
		fd_key_value = open("/sys/class/aml_keys/aml_keys/key_write", O_WRONLY);
		if( fd_key_value < 0) 
		{
			error_info = "error : can't open key_write file!";
			LOGI("%s \n",error_info);
		}

		w_value_num = write(fd_key_value, buf_value , len_value);

		if( w_value_num < 0) 
		{
			error_info = "error : can't write key_write file!";
			LOGI("%s \n",error_info);
		}

		close(fd_key_value);

		//send error info 
		if(send(s_accept,error_info,strlen(error_info),0)==-1)
		{
			error_info = "error : can't send error info!";
			LOGI("%s \n",error_info);
		}
	}else if(recv_buf[0] == 'r'){
		if (open("/sys/class/aml_keys/aml_keys/version", O_RDONLY)< 0){
			fd_use_data = open("/sys/class/efuse/userdata", O_RDONLY);
			if(fd_use_data < 0 )
			{
				error_info = "error : can't open userdata file!";
				LOGI("%s \n",error_info);
			}else{
				buf_use_data = (char *)malloc(BUF_SIZE);
				use_data_info = read(fd_use_data, buf_use_data ,BUF_SIZE);
				if(use_data_info < 0)
				{
					error_info = "error : can't read  userdata file!";
					LOGI("%s \n",error_info);
				}
				else{
					if(send(s_accept,buf_use_data,strlen(buf_use_data),0)==-1)
					{
						error_info = "error : send use data info error !";
						LOGI("%s \n",error_info);
					}
				}
					free(buf_use_data);
					close(fd_use_data);
			}
						
		}else {

			j = 0 ;
			n = 0 ;
			for(i=2;i< recv_bytes ;i++)
			{	
				key_name_buf[j] = recv_buf[i];
				j++;
			}
			//check version 
			fd_version_check = open("/sys/class/aml_keys/aml_keys/key_write", O_WRONLY);
			if( fd_version_check >= 0 )
			{
				isWrited = 1;
			}else{
				isWrited = 0;
			}		
			if(isWrited == 0)
			{
				isWrited = 1;
				fd_version = open("/sys/class/aml_keys/aml_keys/version", O_WRONLY);
				if( fd_version < 0) 
				{
					error_info = "error : can't open version file!";
					LOGI("%s \n",error_info);
				}else{
					w_version_num = write(fd_version, default_version , 5);
					if( w_version_num < 0) 
					{
						error_info = "error : can't write version file!";
						LOGI("%s \n",error_info);
					}
				}
					close(fd_version);
					sleep(3);
			}
			//write key name for read
			fd_key_name = open("/sys/class/aml_keys/aml_keys/key_name", O_WRONLY);
			if( fd_key_name < 0) 
			{
				error_info = "error : can't open key_name file!";
				LOGI("%s \n",error_info);
			}
			len_name = strlen(key_name_buf);
			buf_name = (char *)malloc(len_name);
			memset(buf_name ,'\0', len_name);
			strcpy(buf_name, key_name_buf);
			w_name_num = write(fd_key_name, buf_name , len_name);
			free(buf_name);
			if( w_name_num < 0) 
			{
				error_info = "error : can't write key_name file!";
				LOGI("%s \n",error_info);
			}
			close(fd_key_name);
			//read key info from  key_read file
			fd_key_read = open("/sys/class/aml_keys/aml_keys/key_read", O_RDONLY);
				if( fd_key_read < 0) 
				{
					error_info = "error : can't open key_read file!";
					LOGI("%s \n",error_info);
				}

			//
			buf_value = (char *)malloc(BUF_SIZE);
			memset(buf_value ,'\0', BUF_SIZE);
			key_read_num = read(fd_key_read, buf_value,BUF_SIZE);
			if( key_read_num < 0) 
			{
				error_info = "error : can't read key_read file!";
				LOGI("%s \n",error_info);
			}

			close(fd_key_read);
					
			//send to socket 
			if(send(s_accept,buf_value,key_read_num,0)==-1)
			{
				error_info = "error : send key info error !";
				LOGI("%s \n",error_info);
			}	
				free(buf_value);

		}
					
			}else	{
				error_info = "error : please check your input string , should begin with 'r' or 'w' !";
				LOGI("%s \n",error_info);
				}
				close(s_accept);
				close(s_control);	
				exit(1);
			}	  			
		}
	}	
	LOGI("logic error !!!");	
	return 1;
}







