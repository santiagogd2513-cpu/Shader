.class public final Lrikka/shizuku/server/KeepAlive;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;

# Runs inside the Shizuku server process (shell or root) right after start.

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public run()V
    .locals 4
    :try_start_0
    new-instance v0, Ljava/io/FileOutputStream;
    const-string v1, "/proc/self/oom_score_adj"
    invoke-direct {v0, v1}, Ljava/io/FileOutputStream;-><init>(Ljava/lang/String;)V
    const-string v1, "-1000"
    invoke-virtual {v1}, Ljava/lang/String;->getBytes()[B
    move-result-object v1
    invoke-virtual {v0, v1}, Ljava/io/FileOutputStream;->write([B)V
    invoke-virtual {v0}, Ljava/io/FileOutputStream;->close()V
    const-string v0, "ShizukuKeepAlive"
    const-string v1, "oom_score_adj = -1000 (protected from low memory killer)"
    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catch_0
    :goto_cmds
    :try_start_1
    const/4 v0, 0x3
    new-array v0, v0, [Ljava/lang/String;
    const/4 v1, 0x0
    const-string v2, "sh"
    aput-object v2, v0, v1
    const/4 v1, 0x1
    const-string v2, "-c"
    aput-object v2, v0, v1
    const/4 v1, 0x2
    const-string v2, "(P=moe.shizuku.privileged.api; cmd deviceidle whitelist +$P; dumpsys deviceidle whitelist +$P; cmd appops set $P RUN_ANY_IN_BACKGROUND allow; cmd appops set $P RUN_IN_BACKGROUND allow; cmd appops set $P SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS allow; am set-standby-bucket $P active; cmd activity set-bg-restriction-level --user 0 $P unrestricted) >/dev/null 2>&1"
    aput-object v2, v0, v1
    invoke-static {}, Ljava/lang/Runtime;->getRuntime()Ljava/lang/Runtime;
    move-result-object v1
    invoke-virtual {v1, v0}, Ljava/lang/Runtime;->exec([Ljava/lang/String;)Ljava/lang/Process;
    move-result-object v0
    invoke-virtual {v0}, Ljava/lang/Process;->waitFor()I
    const-string v0, "ShizukuKeepAlive"
    const-string v1, "manager app exempted from battery restrictions"
    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catch_1
    return-void
    :catch_0
    move-exception v0
    goto :goto_cmds
    :catch_1
    move-exception v0
    return-void
.end method
