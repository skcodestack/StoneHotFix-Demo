package github.com.stonehotfix;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.os.Environment;
import android.util.Log;

import github.com.stonehotfix.util.FixDexUtil;

/**
 * Email  1562363326@qq.com
 * Github https://github.com/skcodestack
 * Created by sk on 2017/4/26
 * Version  1.0
 * Description:
 */

public class BaseApplication extends Application {


    private static final String TAG = "BaseApplication";

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
