    # hkg-climate-page hook (GPL-3.0): cM() builds our page and registers it as id 4, like the stock pages
    # (uses only v0, v2, v3: v1 holds the pz monitor; a Throwable leaves the page absent)
    :hkg_try_start
    iget-object v2, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->mContext:Landroid/content/Context;

    iget-object v3, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->mHandler:Landroid/os/Handler;

    invoke-static {v2, v3}, Lhkgclimate/page/ClimatePageFactory;->create(Landroid/content/Context;Landroid/os/Handler;)Lcom/mobis/splitscreen/right/k;

    move-result-object v0

    iput-object v0, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->hkgClimatePage:Lcom/mobis/splitscreen/right/k;

    if-eqz v0, :hkg_done

    const/4 v2, 0x4

    invoke-virtual {v0, v2}, Lcom/mobis/splitscreen/right/k;->bh(I)V

    iget-object v2, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->oa:Ljava/util/ArrayList;

    invoke-virtual {v2, v0}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    invoke-static {}, Landroid/os/Message;->obtain()Landroid/os/Message;

    move-result-object v2

    const/16 v3, 0x3f5

    iput v3, v2, Landroid/os/Message;->what:I

    const/4 v3, 0x4

    iput v3, v2, Landroid/os/Message;->arg1:I

    iput-object v0, v2, Landroid/os/Message;->obj:Ljava/lang/Object;

    iget-object v3, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->pv:Lcom/mobis/splitscreen/right/n/l;

    invoke-virtual {v3, v2}, Lcom/mobis/splitscreen/right/n/l;->sendMessage(Landroid/os/Message;)Z

    iget-object v0, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->od:Ljava/util/ArrayList;

    const/4 v2, 0x4

    const v3, 0x7f0a0090

    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v3

    invoke-virtual {v0, v2, v3}, Ljava/util/ArrayList;->set(ILjava/lang/Object;)Ljava/lang/Object;

    iget v0, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->ou:I

    add-int/lit8 v0, v0, 0x1

    iput v0, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->ou:I
    :hkg_try_end
    .catch Ljava/lang/Throwable; {:hkg_try_start .. :hkg_try_end} :hkg_failed

    goto :hkg_done

    :hkg_failed
    move-exception v0

    const/4 v0, 0x0

    iput-object v0, p0, Lcom/mobis/splitscreen/right/SplitScreenHome;->hkgClimatePage:Lcom/mobis/splitscreen/right/k;

    :hkg_done
