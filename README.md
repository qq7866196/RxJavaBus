# RxJavaBus
## 测试版，会不断的改进，欢迎大家指正。
## 构建项目主要参考EventBus，做了如下修改：
* 去掉了反射机制和注解机制
* 简化了集合的使用
* 利用rxjava达到线程切换目的。
## 使用方法：
* 注册类需要实现接口：ISubscriber
1. 注册一种数据接收：RxJavaBus.getDefault().register(this, EventTest.class);
2. 注册多种数据接收： RxJavaBus.getDefault().register(this, new Class[]{EventTest.class, OtherTest.class});
> * EventTest，OtherTest为你需要传递的事件类型
> * 注意一个类只能注册一次，如果需要更改，需要先取消注册，然后再注册，方可生效。

* 发送：RxJavaBus.getDefault().post(new OtherTest(),AndroidSchedulers.mainThread());//第一个参数是你要传递的事件，第二个参数为你指定接收的线程。

* 接收：在实现接口中onEvent方法中返回。这里接收所有注册的事件，按你的需要进行分发。

## 未来功能：粘性事件，生命周期管理,性能优化及内存泄露排查

## V0.1：测试版提交
