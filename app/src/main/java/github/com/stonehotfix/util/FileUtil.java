package github.com.stonehotfix.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

/**
 * Email  1562363326@qq.com
 * Github https://github.com/skcodestack
 * Created by sk on 2017/4/28
 * Version  1.0
 * Description: 文件操作辅助工具，提供：【拷贝，删除】
 *
 */

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
