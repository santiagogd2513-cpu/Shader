.class public final Lmoe/shizuku/manager/keepalive/KeepAliveReceiver;
.super Lmoe/shizuku/manager/receiver/BootCompleteReceiver;

# Same start logic as BootCompleteReceiver (root / wireless debugging), but as a
# private always-enabled component that only reacts to KEEPALIVE_RESTART.

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Lmoe/shizuku/manager/receiver/BootCompleteReceiver;-><init>()V
    return-void
.end method
