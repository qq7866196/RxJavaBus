package com.example.bustest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.fastcat.rxjavabus.ISubscriber
import com.fastcat.rxjavabus.RxJavaBus

class MainActivity : AppCompatActivity(), ISubscriber {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        RxJavaBus.getDefault().postSticky(EventStickyTest())
        RxJavaBus.getDefault().register(this, EventTest::class.java, EventStickyTest::class.java);
        send()
    }

    override fun onEvent(o: Any?, tag: String?) {
        if (o is EventStickyTest) {
            Toast.makeText(baseContext, "哈哈", Toast.LENGTH_LONG).show()
        } else {
            //Toast.makeText(baseContext, "123", Toast.LENGTH_LONG).show()
        }
    }

    fun send() {
        RxJavaBus.getDefault().post(EventTest())
    }

    override fun onDestroy() {
        super.onDestroy()
        RxJavaBus.getDefault().unregister(this)
    }


}
