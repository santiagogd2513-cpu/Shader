.class public final Lmoe/shizuku/manager/keepalive/KeepAliveService;
.super Landroid/app/Service;
.implements Ljava/lang/Runnable;

# Watchdog: foreground service that checks every 15 s whether the Shizuku
# server binder is alive and, if it died, asks KeepAliveReceiver to start it
# again (root or wireless-debugging, same logic as "start on boot").

.field public handler:Landroid/os/Handler;
.field public fails:I

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Landroid/app/Service;-><init>()V
    return-void
.end method

.method public static start(Landroid/content/Context;)V
    .locals 3
    :try_start_0
    new-instance v0, Landroid/content/Intent;
    const-class v1, Lmoe/shizuku/manager/keepalive/KeepAliveService;
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I
    const/16 v2, 0x1a
    if-lt v1, v2, :cond_old
    invoke-virtual {p0, v0}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;
    goto :goto_end
    :cond_old
    invoke-virtual {p0, v0}, Landroid/content/Context;->startService(Landroid/content/Intent;)Landroid/content/ComponentName;
    :goto_end
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0
    return-void
    :catch_0
    move-exception v0
    const-string v1, "ShizukuKeepAlive"
    const-string v2, "cannot start keep-alive service"
    invoke-static {v1, v2, v0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-void
.end method

.method private goForeground()V
    .locals 6
    :try_start_0
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
    const/16 v1, 0x1a
    const-string v2, "keepalive"
    if-lt v0, v1, :cond_pre26
    const-string v3, "notification"
    invoke-virtual {p0, v3}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v3
    check-cast v3, Landroid/app/NotificationManager;
    new-instance v4, Landroid/app/NotificationChannel;
    const-string v5, "Shizuku siempre activo"
    const/4 v1, 0x2
    invoke-direct {v4, v2, v5, v1}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V
    invoke-virtual {v3, v4}, Landroid/app/NotificationManager;->createNotificationChannel(Landroid/app/NotificationChannel;)V
    new-instance v3, Landroid/app/Notification$Builder;
    invoke-direct {v3, p0, v2}, Landroid/app/Notification$Builder;-><init>(Landroid/content/Context;Ljava/lang/String;)V
    goto :goto_built
    :cond_pre26
    new-instance v3, Landroid/app/Notification$Builder;
    invoke-direct {v3, p0}, Landroid/app/Notification$Builder;-><init>(Landroid/content/Context;)V
    :goto_built
    const v4, 0x7f0800cc
    invoke-virtual {v3, v4}, Landroid/app/Notification$Builder;->setSmallIcon(I)Landroid/app/Notification$Builder;
    const-string v4, "Shizuku: modo siempre activo"
    invoke-virtual {v3, v4}, Landroid/app/Notification$Builder;->setContentTitle(Ljava/lang/CharSequence;)Landroid/app/Notification$Builder;
    const-string v4, "Vigilando el servicio de Shizuku. Si se detiene, se reinicia solo."
    invoke-virtual {v3, v4}, Landroid/app/Notification$Builder;->setContentText(Ljava/lang/CharSequence;)Landroid/app/Notification$Builder;
    const/4 v4, 0x1
    invoke-virtual {v3, v4}, Landroid/app/Notification$Builder;->setOngoing(Z)Landroid/app/Notification$Builder;
    invoke-virtual {v3}, Landroid/app/Notification$Builder;->build()Landroid/app/Notification;
    move-result-object v3
    const/16 v4, 0x22
    const/16 v5, 0x1337
    if-lt v0, v4, :cond_pre34
    const/high16 v4, 0x40000000
    invoke-virtual {p0, v5, v3, v4}, Landroid/app/Service;->startForeground(ILandroid/app/Notification;I)V
    goto :goto_done
    :cond_pre34
    invoke-virtual {p0, v5, v3}, Landroid/app/Service;->startForeground(ILandroid/app/Notification;)V
    :goto_done
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0
    return-void
    :catch_0
    move-exception v0
    const-string v1, "ShizukuKeepAlive"
    const-string v2, "startForeground failed"
    invoke-static {v1, v2, v0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    return-void
.end method

.method public onCreate()V
    .locals 4
    invoke-super {p0}, Landroid/app/Service;->onCreate()V
    invoke-direct {p0}, Lmoe/shizuku/manager/keepalive/KeepAliveService;->goForeground()V
    new-instance v0, Landroid/os/Handler;
    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
    move-result-object v1
    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
    iput-object v0, p0, Lmoe/shizuku/manager/keepalive/KeepAliveService;->handler:Landroid/os/Handler;
    const-wide/16 v2, 0x4e20
    invoke-virtual {v0, p0, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
    return-void
.end method

.method public onStartCommand(Landroid/content/Intent;II)I
    .locals 1
    invoke-direct {p0}, Lmoe/shizuku/manager/keepalive/KeepAliveService;->goForeground()V
    const/4 v0, 0x1
    return v0
.end method

.method public onDestroy()V
    .locals 1
    iget-object v0, p0, Lmoe/shizuku/manager/keepalive/KeepAliveService;->handler:Landroid/os/Handler;
    if-eqz v0, :cond_0
    invoke-virtual {v0, p0}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V
    :cond_0
    invoke-super {p0}, Landroid/app/Service;->onDestroy()V
    return-void
.end method

.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .locals 1
    const/4 v0, 0x0
    return-object v0
.end method

.method public run()V
    .locals 4
    :try_start_0
    invoke-static {}, Lrikka/shizuku/Nn;->h()Z
    move-result v0
    if-eqz v0, :cond_dead
    const/4 v0, 0x0
    iput v0, p0, Lmoe/shizuku/manager/keepalive/KeepAliveService;->fails:I
    goto :goto_sched
    :cond_dead
    iget v0, p0, Lmoe/shizuku/manager/keepalive/KeepAliveService;->fails:I
    add-int/lit8 v0, v0, 0x1
    iput v0, p0, Lmoe/shizuku/manager/keepalive/KeepAliveService;->fails:I
    const/4 v1, 0x2
    if-lt v0, v1, :goto_sched
    const/4 v0, 0x0
    iput v0, p0, Lmoe/shizuku/manager/keepalive/KeepAliveService;->fails:I
    const-string v0, "ShizukuKeepAlive"
    const-string v1, "Shizuku server is not running, restarting it..."
    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    new-instance v0, Landroid/content/Intent;
    const-class v1, Lmoe/shizuku/manager/keepalive/KeepAliveReceiver;
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
    const-string v1, "moe.shizuku.privileged.api.action.KEEPALIVE_RESTART"
    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;
    invoke-virtual {p0, v0}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V
    :goto_sched
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0
    :goto_post
    iget-object v0, p0, Lmoe/shizuku/manager/keepalive/KeepAliveService;->handler:Landroid/os/Handler;
    const-wide/16 v2, 0x3a98
    invoke-virtual {v0, p0, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
    return-void
    :catch_0
    move-exception v0
    goto :goto_post
.end method
