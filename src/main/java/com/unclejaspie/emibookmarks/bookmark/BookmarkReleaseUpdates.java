package com.unclejaspie.emibookmarks.bookmark;

import com.google.gson.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Public release metadata only. Jar versions, not short GitHub tag names, determine ordering. */
public final class BookmarkReleaseUpdates {
    public static final String API = "https://api.github.com/repos/UncleJaspie/emibookmarks/releases?per_page=100";
    private static final Pattern VERSION = Pattern.compile("(?i)^v?(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})(?:[-.](alpha|beta|rc)[.-]?(\\d{1,9}))?(?:\\+[A-Za-z0-9.-]{1,64})?$");
    private static final String PREFIX = "emibookmarks-", TARGET = "-forge-1.20.1.jar";
    public record Release(String version, URI page) {
        public Release {
            if (parse(version)==null || !trusted(page)) throw new IllegalArgumentException("Invalid update release");
        }
    }
    private record Version(int major,int minor,int patch,int channel,int serial) implements Comparable<Version> {
        @Override public int compareTo(Version other) {
            int[] a={major,minor,patch,channel,serial}, b={other.major,other.minor,other.patch,other.channel,other.serial};
            for(int i=0;i<a.length;i++){int order=Integer.compare(a[i],b[i]);if(order!=0)return order;}return 0;
        }
    }
    private static Version parse(String text) {
        if(text==null||text.length()>128)return null;
        var match=VERSION.matcher(text.trim());if(!match.matches())return null;
        int channel=match.group(4)==null?3:switch(match.group(4).toLowerCase(Locale.ROOT)){case "alpha"->0;case "beta"->1;default->2;};
        return new Version(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),Integer.parseInt(match.group(3)),channel,match.group(5)==null?0:Integer.parseInt(match.group(5)));
    }
    public static boolean supportedVersion(String installed){return parse(installed)!=null;}
    private static boolean trusted(URI uri) {
        return uri!=null&&"https".equalsIgnoreCase(uri.getScheme())&&"github.com".equalsIgnoreCase(uri.getHost())&&uri.getRawUserInfo()==null&&uri.getPort()==-1&&uri.getRawQuery()==null&&uri.getRawFragment()==null&&uri.getRawPath()!=null&&uri.getRawPath().toLowerCase(Locale.ROOT).startsWith("/unclejaspie/emibookmarks/releases/tag/")&&uri.toString().length()<2048;
    }
    public static Optional<Release> newer(JsonArray releases,String installed) {
        var current=parse(installed);if(current==null)return Optional.empty();
        Release best=null;Version bestVersion=current;
        for(int i=0;i<Math.min(100,releases.size());i++)try {
            var release=releases.get(i).getAsJsonObject();
            if(release.has("draft")&&release.get("draft").getAsBoolean())continue;
            if(current.channel==3&&release.has("prerelease")&&release.get("prerelease").getAsBoolean())continue;
            var page=URI.create(release.get("html_url").getAsString());if(!trusted(page))continue;
            var assets=release.getAsJsonArray("assets");
            for(int a=0;a<Math.min(100,assets.size());a++)try {
                String name=assets.get(a).getAsJsonObject().get("name").getAsString();
                if(!name.startsWith(PREFIX)||name.length()>256)continue;
                String version;
                if(name.endsWith(TARGET))version=name.substring(PREFIX.length(),name.length()-TARGET.length());
                else if(name.endsWith(".jar"))version=name.substring(PREFIX.length(),name.length()-4); // Original single-target beta jars.
                else continue;
                var parsed=parse(version);
                if(parsed==null||current.channel==3&&parsed.channel!=3||parsed.compareTo(bestVersion)<=0)continue;
                best=new Release(version,page);bestVersion=parsed;
            } catch(RuntimeException malformedAsset){ /* Ignore one malformed/foreign asset. */ }
        } catch(RuntimeException malformedRelease){ /* Keep other well-formed published releases usable. */ }
        return Optional.ofNullable(best);
    }
}
