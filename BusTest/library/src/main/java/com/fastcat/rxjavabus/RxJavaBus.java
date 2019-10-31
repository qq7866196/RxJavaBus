package com.fastcat.rxjavabus;

import android.support.annotation.NonNull;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by ws on 2017/8/31 0031.
 * 未来功能：粘性事件，生命周期管理,性能优化及内存泄露排查
 */

public class RxJavaBus {
    /**
     * Log tag, apps may override it.
     */
    public static String TAG = "RxJavaBus";
    private final ArrayList<Class> stickyEvents;
    private final Map<Class, List<Subscription>> subscribersByEvent;
    static volatile RxJavaBus defaultInstance;
    private static final Builder DEFAULT_BUILDER = new Builder();
    private ArrayList<Disposable> disposables;

    public static RxJavaBus getDefault() {
        if (defaultInstance == null) {
            synchronized (RxJavaBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new RxJavaBus();
                }
            }
        }
        return defaultInstance;
    }

    public RxJavaBus() {
        this(DEFAULT_BUILDER);
    }

    RxJavaBus(Builder builder) {
        subscribersByEvent = new ConcurrentHashMap<>();
        stickyEvents = new ArrayList<>();
        disposables = new ArrayList<>();
    }


    public void register(@NonNull ISubscriber subscriber, @NonNull Class event) {
        if (subscriber == null || event == null) {
            throw new RxJavaBusException("subscriber or event is null");
        }
        //tagsByEvent.put(event, subscriber.getTags());
        synchronized (this) {
            subscribe(subscriber, event);
        }
     /*   //发送粘性事件
        for (Object stickyEvent : stickyEvents) {
            subscriber.onEvent(stickyEvent,"");
            ArrayList<ISubscriber> arrayList = (ArrayList) subscribersByEvent.get(stickyEvent);
            if (arrayList == null) {
                subscribersByEvent.put(event.getClass(), new ArrayList<ISubscriber>());
            } else {
                Iterator iterator = arrayList.iterator();
                while (iterator.hasNext()) {
                    ISubscriber sub = (ISubscriber) iterator.next();
                    sub.onEvent(event, "");
                    //unregister(sub, event.getClass());
                }
            }
        }*/
    }

    public void register(@NonNull ISubscriber subscriber, @NonNull Class[] events) {
        if (subscriber == null || events == null) {
            throw new RxJavaBusException("subscriber or events is null");
        }
        synchronized (this) {
            for (Class event : events) {
                subscribe(subscriber, event);
            }
        }
    }

    private void subscribe(ISubscriber subscriber, Class event) {
        ArrayList subList;
        Subscription subscription = new Subscription(subscriber);
        if (subscribersByEvent.get(event) == null) {
            subList = new ArrayList();
            subList.add(subscription);
        } else {
            subList = (ArrayList) subscribersByEvent.get(event);
            if (subList.contains(subscription)) {
                return;
                /*throw new RxJavaBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + event.getClass());*/
            } else {
                subList.add(subscription);
            }
        }
        subscribersByEvent.put(event, subList);
    }

    public void post(Object event) {
        post(event, null, null);
    }

    //指定接收线程
    public void post(Object event, Scheduler receive) {
        post(event,null, receive);
    }

    //指定发送和接收线程
    public void post(final Object event, Scheduler send, Scheduler receive) {
        if (event == null) {
            throw new RxJavaBusException("params is null");
        }
        //不使用链式，就切换线程无效？
        Disposable disposable =   Flowable.create(new FlowableOnSubscribe<Object>() {
            @Override
            public void subscribe(FlowableEmitter<Object> e) throws Exception {
                //根据线程设置，在该线程中完成以下操作,这里设置的是新的IO线程
                //数据库查询操作等
                e.onNext(event);
                e.onComplete();
            }
        }, BackpressureStrategy.BUFFER)
                .subscribeOn(Schedulers.computation())
                .observeOn(receive)
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object o) throws Exception {
                        //根据线程设置，在该线程中完成以下操作,这里设置的是UI线程
                        List<Subscription> arrayList = Collections.synchronizedList(subscribersByEvent.get(o.getClass()));
                        if (arrayList == null) {
                            subscribersByEvent.put(o.getClass(), new ArrayList<Subscription>());
                        } else {
                            Iterator iterator = arrayList.iterator();
                            while (iterator.hasNext()) {
                                Subscription sub = (Subscription) iterator.next();
                                sub.subscriber.onEvent(o, "");
                            }
                        }
                    }
                });
        disposables.add(disposable);
   /*
    //无效
   Flowable flowable = Flowable.create(new FlowableOnSubscribe<Object>() {
            @Override
            public void subscribe(FlowableEmitter<Object> e) throws Exception {
                //根据线程设置，在该线程中完成以下操作,这里设置的是新的IO线程
                //数据库查询操作等
                e.onNext(event);
                e.onComplete();
            }
        }, BackpressureStrategy.BUFFER);
        if (send != null) {
            flowable.subscribeOn(send);
        }
        if (receive != null) {
            flowable.observeOn(receive);
        }
        Disposable disposable = flowable.subscribe(new Consumer<Object>() {
            @Override
            public void accept(Object o) throws Exception {
                //根据线程设置，在该线程中完成以下操作,这里设置的是UI线程
                List<Subscription> arrayList = Collections.synchronizedList(subscribersByEvent.get(o.getClass()));
                if (arrayList == null) {
                    subscribersByEvent.put(o.getClass(), new ArrayList<Subscription>());
                } else {
                    Iterator iterator = arrayList.iterator();
                    while (iterator.hasNext()) {
                        Subscription sub = (Subscription) iterator.next();
                        sub.subscriber.onEvent(o, "");
                    }
                }
            }
        });
        disposables.add(disposable);*/

    }

    public void post(Object event, String action) {
        if (event == null) {
            throw new RxJavaBusException("event is null");
        }
        ArrayList<Subscription> arrayList = (ArrayList) subscribersByEvent.get(event.getClass());
        if (arrayList != null) {
            Iterator iterator = arrayList.iterator();
            while (iterator.hasNext()) {
                Subscription sub = (Subscription) iterator.next();
                sub.subscriber.onEvent(event, action);
            }
        }
    }

    public void postSticky(Object event) {
        if (event == null) {
            throw new RxJavaBusException("event is null");
        }
        synchronized (stickyEvents) {
            stickyEvents.add(event.getClass());
        }
        post(event);
    }

    public void unregister(ISubscriber subscriber, @NonNull Class event) {
        if (subscriber == null) {
            throw new RxJavaBusException("subscriber is null");
        }
        ArrayList subList;
        if (subscribersByEvent.get(event) == null) {
            return;
        } else {
            subList = (ArrayList) subscribersByEvent.get(event);
        }
        subList.remove(subscriber);
        subscribersByEvent.put(event, subList);
    }
    
    public void unregister(ISubscriber subscriber, @NonNull Class[] events) {
        if (subscriber == null) {
            throw new RxJavaBusException("subscriber is null");
        }
        for(Class event:events){
            unregister(subscriber,event);
        }
    }

    static class Builder {

    }

    /**
     * 移除指定eventType的Sticky事件
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }


    /**
     * 移除所有的Sticky事件
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    public void cancelAllPost(){
        if(disposables!=null){
            for(Disposable disposable:disposables){
                disposable.dispose();
            }
        }
    }

    public void clearCache() {
        if (stickyEvents != null) {
            stickyEvents.clear();
        }
        if (subscribersByEvent != null) {
            subscribersByEvent.clear();
        }
        if(disposables!=null){
            for(Disposable disposable:disposables){
                disposable.dispose();
            }
        }
    }

}
