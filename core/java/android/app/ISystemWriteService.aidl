package android.app;
/**
 * {@hide}
 */
interface ISystemWriteService {
	 String   getProperty(String key);
	 String   getPropertyString(String key,String def);
	 int      getPropertyInt(String key,int def);
	 long     getPropertyLong(String key,long def);
	 boolean  getPropertyBoolean(String key,boolean def);
	 void     setProperty(String key, String value);
	 
	 String   readSysfs(String path);
	 boolean  writeSysfs(String path, String value);
}