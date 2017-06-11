package github.com.stonehotfix;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;

import github.com.stonehotfix.util.FileUtil;
import github.com.stonehotfix.util.FixDexUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG="MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


    }

    public void btn_fix(View view){

        Log.e(TAG, "开始修复。。。。");
        try {

            File dexDir = this.getDir("tem",MODE_PRIVATE);// "/dex/";
            String dexPath=dexDir.getAbsolutePath()+"fix.dex";
            FileUtil.copyAssetsTo(this,"fix.dex",dexPath);
            File file=new File(dexPath);
            if (file.exists()) {
//                Fix.injectDexElements(MainActivity.this, dexDir.getAbsolutePath());
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
}
