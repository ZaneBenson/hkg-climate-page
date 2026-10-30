# hkg-climate-page test fixture (GPL-3.0): a made-up class written for tests/panel/test_patch_panel.py.
# It has only the shape tools/patch-panel.py looks for (instance fields, cM()V with .registers 10
# and :cond_a55 inside a monitor block, aS(I) with a packed switch, :pswitch_1b8 and :goto_2d).
# None of its code comes from the stock panel.
.class public final Lcom/mobis/splitscreen/right/SplitScreenHome;
.super Lcom/mobis/splitscreen/right/k/q;


# static fields
.field private static lock:Ljava/lang/Object;


# instance fields
.field private final mHandler:Landroid/os/Handler;

.field private volatile oa:Ljava/util/ArrayList;

.field private od:Ljava/util/ArrayList;

.field private ou:I

.field private volatile pv:Lcom/mobis/splitscreen/right/n/l;


# direct methods
.method public constructor <init>()V
    .registers 1

    invoke-direct {p0}, Lcom/mobis/splitscreen/right/k/q;-><init>()V

    return-void
.end method

.method private aS(I)Lcom/mobis/splitscreen/right/k;
    .registers 3

    const/4 v0, 0x0

    const/4 v1, 0x0

    packed-switch p1, :pswitch_data_10

    :goto_2d
    return-object v0

    :pswitch_1b8
    move-object v0, v1

    goto :goto_2d

    :pswitch_c
    move-object v0, v1

    goto :goto_2d

    :pswitch_data_10
    .packed-switch 0x3
        :pswitch_c
        :pswitch_1b8
    .end packed-switch
.end method

.method private cM()V
    .registers 10

    sget-object v1, Lcom/mobis/splitscreen/right/SplitScreenHome;->lock:Ljava/lang/Object;

    monitor-enter v1

    :try_start_0
    iget v0, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->ou:I

    if-eqz v0, :cond_a55

    const/4 v0, 0x0

    iput v0, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->ou:I

    :cond_a55
    monitor-exit v1
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    return-void

    :catchall_0
    move-exception v0

    monitor-exit v1

    throw v0
.end method
