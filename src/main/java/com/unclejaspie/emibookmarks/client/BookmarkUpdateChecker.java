package com.unclejaspie.emibookmarks.client;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.unclejaspie.emibookmarks.Emibookmarks;
import com.unclejaspie.emibookmarks.bookmark.BookmarkReleaseUpdates;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Quiet client-only public release lookup. Never downloads or installs a mod. */
@Mod.EventBusSubscriber(modid=Emibookmarks.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BookmarkUpdateChecker {
    private static final long INTERVAL=Duration.ofHours(6).toNanos(), RETRY=Duration.ofMinutes(30).toNanos();
    private static final int MAX_BYTES=2*1024*1024;
    private static BookmarkReleaseUpdates.Release available;
    private static String dismissed,etag,installed;
    private static long nextCheck;
    private static boolean inFlight;
    private static Status status=Status.IDLE;
    private enum Status { IDLE, CHECKING, CURRENT, AVAILABLE, UNAVAILABLE }
    private record Response(Optional<BookmarkReleaseUpdates.Release> release,String etag,boolean unchanged) {}
    private record Notice(int x,int y,int width,int height,String text,BookmarkReleaseUpdates.Release release) {
        boolean contains(double mx,double my){return mx>=x&&my>=y&&mx<x+width&&my<y+height;}
    }
    @SubscribeEvent public static void opened(ScreenEvent.Init.Post event) {
        if(event.getScreen() instanceof AbstractContainerScreen<?>)check();
    }
    private static void check() {
        if(!BookmarkClientConfig.CHECK_UPDATES.get()||inFlight)return;
        long now=System.nanoTime();if(nextCheck!=0&&now-nextCheck<0)return;
        if(installed==null)installed=ModList.get().getModContainerById(Emibookmarks.MODID).orElseThrow().getModInfo().getVersion().toString();
        if(!BookmarkReleaseUpdates.supportedVersion(installed))return;
        nextCheck=now+INTERVAL;inFlight=true;status=Status.CHECKING;
        String current=installed,previousEtag=etag;
        CompletableFuture.supplyAsync(()->{
            try{return fetch(current,previousEtag);}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((response,error)->Minecraft.getInstance().execute(()->{
            inFlight=false;
            if(error!=null){status=Status.UNAVAILABLE;nextCheck=System.nanoTime()+RETRY;LogUtils.getLogger().debug("EMI Bookmarks update check unavailable; retry deferred");return;}
            if(!response.unchanged)available=response.release.orElse(null);
            etag=response.etag;status=available==null?Status.CURRENT:Status.AVAILABLE;
            LogUtils.getLogger().debug("EMI Bookmarks release check complete: {}",status);
        }));
    }
    private static Response fetch(String current,String previousEtag)throws IOException {
        var connection=(java.net.HttpURLConnection)URI.create(BookmarkReleaseUpdates.API).toURL().openConnection();
        connection.setConnectTimeout(5000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");connection.setRequestProperty("User-Agent","EMI-Bookmarks/"+current);
        if(previousEtag!=null)connection.setRequestProperty("If-None-Match",previousEtag);
        try {
            int status=connection.getResponseCode();
            String nextEtag=Optional.ofNullable(connection.getHeaderField("ETag")).filter(s->s.length()<256&&!s.contains("\r")&&!s.contains("\n")).orElse(null);
            if(status==304)return new Response(Optional.empty(),previousEtag,true);
            if(status!=200)throw new IOException("GitHub release HTTP "+status);
            try(var body=connection.getInputStream()) {
                byte[] bytes=body.readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new IOException("Release metadata exceeds limit");
                var releases=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
                return new Response(BookmarkReleaseUpdates.newer(releases,current),nextEtag,false);
            }
        } finally {connection.disconnect();}
    }
    private static Notice notice(AbstractContainerScreen<?> screen) {
        var update=available;
        if(!BookmarkClientConfig.CHECK_UPDATES.get()||update==null||update.version().equals(dismissed))return null;
        var font=Minecraft.getInstance().font;int x=6,y=screen.height-31,width=screen.width-12;
        // Stay above EMI's bottom controls; avoid a container's own slots on short screens.
        if(y+font.lineHeight>screen.getGuiTop()&&y<screen.getGuiTop()+screen.getYSize())width=Math.min(width,screen.getGuiLeft()-12);
        if(width<font.width("Update :)"))return null;
        String full="New EMI Bookmarks update available :)";
        String text=font.width(full)<=width?full:font.plainSubstrByWidth("Bookmarks update",width-font.width("..."))+"...";
        return new Notice(x,y,font.width(text),font.lineHeight,text,update);
    }
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen))return;
        var notice=notice(screen);if(notice==null)return;
        var g=event.getGuiGraphics();var font=Minecraft.getInstance().font;boolean hover=notice.contains(event.getMouseX(),event.getMouseY());
        g.pose().pushPose();g.pose().translate(0,0,500);
        g.drawString(font,notice.text,notice.x,notice.y,hover?0xBFDAF0:0x96B7CF,true);
        if(hover)g.renderComponentTooltip(font,java.util.List.of(Component.literal("EMI Bookmarks "+notice.release.version()+" is available"),
                Component.literal("Left click: open GitHub release").withStyle(net.minecraft.ChatFormatting.GRAY),
                Component.literal("Right click: hide this update for this session").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)),event.getMouseX(),event.getMouseY());
        g.pose().popPose();
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen))return;
        var notice=notice(screen);if(notice==null||!notice.contains(event.getMouseX(),event.getMouseY()))return;
        if(event.getButton()==1){dismissed=notice.release.version();event.setCanceled(true);}
        else if(event.getButton()==0){
            String url=notice.release.page().toString();var mc=Minecraft.getInstance();
            mc.setScreen(new ConfirmLinkScreen(confirmed->{if(confirmed)Util.getPlatform().openUri(url);mc.setScreen(screen);},url,true));event.setCanceled(true);
        }
    }
}
