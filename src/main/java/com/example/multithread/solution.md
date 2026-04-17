 ---                                                                          
CompletableFuture 是什么

可以理解成把任务交给别人去做，自己不等，等所有人做完再收结果。                                                                                                                                           
// 现在（你自己一个一个做）                                                                                                                                                                              
你 → 打电话给A服务 → 等A回复 → 打电话给B服务 → 等B回复 → ...

// CompletableFuture（同时交出去，等所有人回来）
你 → 同时派出5个人分别打电话 → 等最慢的那个回来 → 收齐结果

  ---
方向A：换线程安全容器

还是有一个共享的篮子，但这个篮子加了锁，同一时刻只允许一个人放东西进去：

线程A ──┐
线程B ──┤──→ 🔒 CopyOnWriteArrayList（排队放，不会冲突）
线程C ──┘

缺点： 每次写都要排队，高并发下性能差。治标不治本，共享状态还在。

  ---
方向B：消灭共享状态

每个线程各自拿一个篮子，装完自己的东西，最后你来统一倒进一个大篮子：

线程A → 自己的篮子[A的结果] ──┐
线程B → 自己的篮子[B的结果] ──┤→ 主线程统一合并 → [A,B,C,D,E的结果]
线程C → 自己的篮子[C的结果] ──┘

合并这一步只有主线程在做，根本没有竞争

代码上对应：
// 方向A：共享一个 list，加锁                                                                                                                                                                            
List<Map> sharedList = new CopyOnWriteArrayList<>();
CompletableFuture.supplyAsync(() -> {
sharedList.addAll(userService());  // 多线程写同一个 list（加锁版）                                                                                                                                  
return null;
});

// 方向B：各自返回结果，最后合并                                                                                                                                                                         
CompletableFuture<List<Map>> futureA = CompletableFuture.supplyAsync(() -> userService());
CompletableFuture<List<Map>> futureB = CompletableFuture.supplyAsync(() -> orderService());

// 主线程等两个都完成，再合并 —— 这里只有一个线程在操作                                                                                                                                                  
List<Map> result = Stream.of(futureA, futureB)
.map(CompletableFuture::join)   // 等待并拿结果                                                                                                                                                      
.flatMap(List::stream)
.collect(Collectors.toList());  // 合并

  ---
总结对比

            线程A    线程B    线程C    主线程
方向A：    写 ──→  🔒list ←── 写      -      有竞争，加锁解决
方向B：    写A结果  写B结果  写C结果   合并    无竞争，设计上消灭

方向B 更好，因为线程各干各的，互不干扰，主线程最后收摊，完全没有锁的开销。


三个版本的核心区别一眼看懂：

同步版本      每个任务 return 结果，但串行等待                                                                                                                                                           
A → B → C → D → E

方向A         每个任务写进同一个 sharedList（加锁排队）
runAsync → sharedList.addAll()  × 5 个并发

方向B         每个任务 return 自己的结果（互不干扰）
supplyAsync → return list       × 5 个并发
主线程 .join() 收齐 → flatMap 合并

方向A 和 B 性能差不多，但方向B 没有锁，代码意图更清晰，是生产中推荐的写法。


=== 性能测试 ===
同步耗时: 1144ms
返回结果数: 5
----------------------------
=== 线程安全测试（共享 ArrayList）===
期望 size: 1000
实际 size: 988
数据丢失了 12 条 → 证明 ArrayList 线程不安全
----------------------------
=== 三版本性能对比 ===
同步版本:          1130ms  ← 各服务耗时之和
方向A (加锁容器):  318ms  ← 最慢服务耗时
方向B (无共享状态):303ms  ← 最慢服务耗时
