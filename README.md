# StoneHotFix-Demo
手写热修复功能



之前我们分析了阿里的热修复框架AndFix的使用和原理，如果没看过的小伙伴可以先去看下，使用起来还是很简单的，但是有个缺陷，就是针对新出的android系统不兼容，因为AndFixNatice层对不同的系统做不同的处理方式，如果版本更新，但是阿里工程师还没来得及添加针对新系统的处理，那么就有可能出问题。



#####我们今天就手写一个针对java层的热修复功能。先来了解一下Android的ClassLoader体系，android中加载类一般使用的是PathClassLoader和DexClassLoader，首先看下这两个类的区别：

1. PathClassLoader

		Android是使用这个类作为其系统类和应用类的加载器。并且对于这个类呢，只能去加载已经安装到Android系统中的apk文件。

2. DexClassLoader

		可以用来从.jar和.apk类型的文件内部加载classes.dex文件。可以用来执行非安装的程序代码



####大家只需要明白，Android使用PathClassLoader作为其类加载器，DexClassLoader可以从.jar和.apk类型的文件内部加载classes.dex文件就好了。且PathClassLoader和DexClassLoader都继承


</br>


我们热修复的原理就是什么呢？

我们先来看下java加载类的方法findClass代码：

BaseDexClassLoader.java:


		@Override
    	protected Class<?> findClass(String name) throws ClassNotFoundException {
	        List<Throwable> suppressedExceptions = new ArrayList<Throwable>();
	        Class c = pathList.findClass(name, suppressedExceptions);
	        if (c == null) {
	           
	            throw cnfe;
	        }
	        return c;
	    }


我们看到BaseDexClassLoader的findClass调用了pathList的findClass,我们接着看：

pathList.java
		
		
		//每个元素对应一个dex文件
		private final Element[] dexElements;

		public Class findClass(String name, List<Throwable> suppressed) {
	        for (Element element : dexElements) {
	            DexFile dex = element.dexFile;
	
	            if (dex != null) {
	                Class clazz = dex.loadClassBinaryName(name, definingContext, suppressed);
	                if (clazz != null) {
	                    return clazz;
	                }
	            }
	        }
	        if (dexElementsSuppressedExceptions != null) {
	            suppressed.addAll(Arrays.asList(dexElementsSuppressedExceptions));
	        }
	        return null;
	    }
		

这边遍历了类变量dexElements，然后调用了DexFile的loadClassBinaryName来获取class的：

DexFile.java

		public Class loadClassBinaryName(String name, ClassLoader loader, List<Throwable> suppressed) {
		        return defineClass(name, loader, mCookie, suppressed);
		}
	
	    private static Class defineClass(String name, ClassLoader loader, int cookie,
	                                     List<Throwable> suppressed) {
	        Class result = null;
	        try {
	            result = defineClassNative(name, loader, cookie);
	        } 
	        return result;
	    }
	
	    private static native Class defineClassNative(String name, ClassLoader loader, int cookie)throws ClassNotFoundException, NoClassDefFoundError;



我们看到最后调用的是native方法。所以我们就深入到这吧，我们主要记住最后DexFile.loadClassBinaryName就可以获得calss了




###综述上面的：
	
####一个ClassLoader可以包含多个dex文件，每个dex文件是一个Element，多个dex文件排列成一个有序的数组dexElements，当找类的时候，会按顺序遍历dex文件，然后从当前遍历的dex文件中找类，如果找类则返回，如果找不到从下一个dex文件继续查找。




##2.热修复的原理

##这样的话我们可以对pathList中dexElements操作，把我们的补丁文件放到dexElements的第一位，那么当加载我们指定的类时，就会先到我们补丁文件中查找，查找到之后返回，这样我们就实现了动态修复的功能。



原理我们知道了，下面就来分析下如何把我们的补丁文件插入到dexElements中，我们主要拿BaseDexClassLoader中的DexPathList，然后在DexPathList中拿到Element，在把我们的补丁文件插入就ok了。


		public class BaseDexClassLoader extends ClassLoader {
    		private final DexPathList pathList;
		}
	
	
		/*package*/ final class DexPathList {
   			private final Element[] dexElements;
		}


我们看到pathList和dexElements都是private,DexPathList类又是本包才能访问，所以我们就想到了反射来获取我们想要的元素，知道原理就行了  ，下面我们来撸代码：



		public class FixDexUtil {

	    private static final String TAG="FixDexUtil";
	
	
	    private static  final  String DEXCLASSLOADER_NAME="dalvik.system.BaseDexClassLoader";
	    private static final String DEXPATHLIST="pathList";
	    private static final String DEXELEMENTS="dexElements";
	    private static final String OPT_DIR="odex";
	    //fix dex 存放目录
	    private File fixDexDir;
	    private Context mContext;
	
	    public FixDexUtil(Context context) {
	        this.mContext=context;
	        fixDexDir = context.getDir(OPT_DIR, Context.MODE_PRIVATE);
	    }
	
	    /**
	     * 修复dex包
	     * @param fixDexPath  dex补丁包的路径
	     */
	    public void fixDex(String fixDexPath) throws Exception{
	
	        //2.获取补丁的DexElement
	        //2.1移动到能够访问的目录下
	        File srcFile=new File(fixDexPath);
	        if(!srcFile.exists()){
	            throw  new FileNotFoundException(srcFile.getAbsolutePath());
	        }
	        File targetFile=new File(fixDexDir,srcFile.getName());
	        if(targetFile.exists()){
	            Log.e(TAG,"文件"+fixDexPath+"已经被加载了！");
	            return ;
	        }
	        FileUtil.copyFile(srcFile,targetFile);
	        //添加到处理列表中
	        List<File> fixDexFiles=new ArrayList<File>() ;
	        fixDexFiles.add(targetFile);
	        //开始修复
	        fixDexFiles(fixDexFiles);
	
	    }
	
	    /**
	     * 合并数组
	     * @param arrayLhs
	     * @param arrayRhs
	     * @return
	     */
	    private Object combineArray(Object arrayLhs,Object arrayRhs){
	        Class<?> localClass = arrayLhs.getClass().getComponentType();
	        int i = Array.getLength(arrayLhs);
	        int j = i + Array.getLength(arrayRhs);
	        Object result = Array.newInstance(localClass, j);
	        for (int k = 0; k < j; ++k) {
	            if (k < i) {
	                Array.set(result, k, Array.get(arrayLhs, k));
	            } else {
	                Array.set(result, k, Array.get(arrayRhs, k - i));
	            }
	        }
	        return result;
	    }
	
	
	    /**
	     * 获取dexElements
	     * @param appClassLoader
	     * @return
	     * @throws Exception
	     */
	    private Object getDexElements(ClassLoader appClassLoader) throws Exception {
	        //得到DexPathlist
	//        Class<?> dexclazz = Class.forName(DEXCLASSLOADER_NAME);
	
	        Class<?> dexclazz =BaseDexClassLoader.class;
	        Field pathListField = dexclazz.getDeclaredField(DEXPATHLIST);
	        pathListField.setAccessible(true);
	        Object pathList = pathListField.get(appClassLoader);
	        //获取Element
	        Class<?> pathListClass = pathList.getClass();
	        Field elementField = pathListClass.getDeclaredField(DEXELEMENTS);
	        elementField.setAccessible(true);
	        Object dexElements = elementField.get(pathList);
	
	        return dexElements;
	    }
	
	    /**
	     * 设置dexElements
	     * @param appClassLoader
	     * @param dexElements
	     * @throws Exception
	     */
	    private void setDexelements(ClassLoader appClassLoader,Object dexElements) throws Exception{
	        //得到DexPathlist
	//        Class<?> dexclazz = Class.forName(DEXCLASSLOADER_NAME);
	        Class<?> dexclazz =BaseDexClassLoader.class;
	        Field pathListField = dexclazz.getDeclaredField(DEXPATHLIST);
	        pathListField.setAccessible(true);
	        Object pathList = pathListField.get(appClassLoader);
	
	        //设置Element
	        Class<?> pathListClass = pathList.getClass();
	        Field elementField = pathListClass.getDeclaredField(DEXELEMENTS);
	        elementField.setAccessible(true);
	        elementField.set(pathList,dexElements);
	
	    }
	
	    /**
	     * 加载全部修复包
	     */
	    public void loadFixDex() throws Exception{
	        File[] dexFiles = fixDexDir.listFiles();
	        List<File> fixDexList=new ArrayList<>();
	
	        for (File dexFile:dexFiles) {
	            if(dexFile.getName().endsWith(".dex")) {
	                fixDexList.add(dexFile);
	            }
	        }
	        //开始修复
	        fixDexFiles(fixDexList);
	    }
	
	    /**
	     * 修复传递进来的列表
	     * @param fixDexList
	     * @throws Exception
	     */
	    private void fixDexFiles(List<File> fixDexList) throws Exception{
	        //1.获取已经运行的DexElement
	        ClassLoader appClassLoader = mContext.getClassLoader();
	        Object dexElements = getDexElements(appClassLoader);
	        Log.e(TAG,"111111111111111");
	        //2.2获取补丁的DexElement
	        //创建优化的目录
	        File optimizedDirectory=new File(fixDexDir,OPT_DIR);
	        if(!optimizedDirectory.exists()){
	            optimizedDirectory.mkdirs();
	        }
	        //开始修复
	        for (File fixFile:fixDexList) {
	            //取补丁的DexElement
	            DexClassLoader fixDexClassLoader=new DexClassLoader(fixFile.getAbsolutePath(),optimizedDirectory.getAbsolutePath(),null,appClassLoader);
	            Object fixDexElements = getDexElements(fixDexClassLoader);
	            Log.e(TAG,"22222222222222");
	            //3.合并DexElement，并且把新元素插入到替换原元素(补丁插入到数组开头)
	            Object newDexElements = combineArray(fixDexElements, dexElements);
	            //设置，替换
	            setDexelements(appClassLoader,newDexElements);
	            Log.e(TAG,"33333333333333");
	        }
	
	    }
	}	




下面给出上面代码文件操作工具类：

		public class FileUtil {
	    /**
	     *
	     * copy file
	     *
	     * @param src
	     *            source file
	     * @param dest
	     *            target file
	     * @throws IOException
	     */
	    public static void copyFile(File src, File dest) throws IOException {
	        FileChannel inChannel = null;
	        FileChannel outChannel = null;
	        try {
	            if (!dest.exists()) {
	                dest.createNewFile();
	            }
	            inChannel = new FileInputStream(src).getChannel();
	            outChannel = new FileOutputStream(dest).getChannel();
	            inChannel.transferTo(0, inChannel.size(), outChannel);
	        } finally {
	            if (inChannel != null) {
	                inChannel.close();
	            }
	            if (outChannel != null) {
	                outChannel.close();
	            }
	        }
	    }
	
	    /**
	     * delete file
	     *
	     * @param file
	     *            file
	     * @return true if delete success
	     */
	    public static boolean deleteFile(File file) {
	        if (!file.exists()) {
	            return true;
	        }
	        if (file.isDirectory()) {
	            File[] files = file.listFiles();
	            for (File f : files) {
	                deleteFile(f);
	            }
	        }
	        return file.delete();
	    }
	
	    public static boolean copyAssetsTo(Context context, String assetsName, String fullPath) {
	        if (TextUtils.isEmpty(assetsName) || TextUtils.isEmpty(fullPath)) {
	            return false;
	        }
	
	        try {
	            File targetFile = new File(fullPath);
	            InputStream inputStream = context.getAssets().open(assetsName);
	
	            if (!targetFile.exists()) {
	                targetFile.getParentFile().mkdir();
	//                targetFile.createNewFile();
	            }
	
	            OutputStream os = null;
	            try {
	                os = new BufferedOutputStream(new FileOutputStream(targetFile));
	                byte data[] = new byte[1024];
	                int len;
	                while ((len = inputStream.read(data, 0, 1024)) != -1) {
	                    os.write(data, 0, len);
	                }
	                os.flush();
	                return true;
	            } catch (IOException e) {
	                e.printStackTrace();
	                Log.e("FD","===>"+e);
	                return false;
	            } finally {
	                if (os != null) {
	                    try {
	                        os.close();
	                    } catch (IOException e) {
	                        e.printStackTrace();
	                    }
	                }
	                if (inputStream != null) {
	                    try {
	                        inputStream.close();
	                    } catch (IOException e) {
	                        e.printStackTrace();
	                    }
	                }
	            }
	
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	        return false;
	    }
	}



##使用

	public class BaseApplication extends Application {
		 	@Override
    		public void onCreate() {
        		super.onCreate();

				try {
			            FixDexUtil fixDexUtil=new FixDexUtil(this);
			            fixDexUtil.loadFixDex();
			            Log.e(TAG,"修复包加载完成！");
			        } catch (Exception e) {
			            e.printStackTrace();
			        }
			}
	}




	
	private void fixDexBug() {
        Log.e(TAG, "开始修复。。。。");
        try {
            File dexDir = this.getDir("tem",MODE_PRIVATE);// "/dex/";
            String dexPath=dexDir.getAbsolutePath()+"fix.dex";
            FileUtil.copyAssetsTo(this,"fix.dex",dexPath);
            File file=new File(dexPath);
            if (file.exists()) {
                FixDexUtil fixUtil = new FixDexUtil(this);
                fixUtil.fixDex(file.getAbsolutePath());
                Log.e(TAG, "修复成功。。。。");
            }

        }catch (Exception ex){
            ex.printStackTrace();
            Log.e(TAG, "修复失败:"+ex);
        }
        Log.e(TAG, "修复结束。。。。");
    }




	
