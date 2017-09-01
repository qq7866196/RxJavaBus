package com.fastcat.rxjavabus;

import java.util.List;

/**
 * Created by Administrator on 2017/8/31 0031.
 */

public interface ISubscriber {
    void onEvent(Object o, String tag);
}
