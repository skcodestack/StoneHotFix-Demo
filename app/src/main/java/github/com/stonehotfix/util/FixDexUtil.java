package github.com.stonehotfix.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;

/**
 * Email  1562363326@qq.com
 * Github https://github.com/skcodestack
 * Created by sk on 2017/4/28
 * Version  1.0
 * Description: 修复dex辅助工具
 */

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
