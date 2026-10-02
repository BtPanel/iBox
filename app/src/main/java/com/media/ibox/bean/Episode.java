package com.media.ibox.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.media.ibox.impl.Diffable;
import com.media.ibox.utils.Util;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import java.util.Locale;
import java.util.Objects;

public class Episode implements Parcelable, Diffable<Episode> {

    @SerializedName("name")
    private String name;
    @SerializedName("desc")
    private String desc;
    @SerializedName("url")
    private String url;

    private int index;
    private int number;
    private boolean selected;

    private Episode(String name, String desc, String url) {
        this.number = Util.getNumber(name);
        this.name = name;
        this.desc = desc;
        this.url = url;
    }

    public Episode() {
    }

    protected Episode(Parcel in) {
        this.name = in.readString();
        this.desc = in.readString();
        this.url = in.readString();
        this.number = in.readInt();
        this.selected = in.readByte() != 0;
    }

    public static Episode create(String name, String url) {
        return new Episode(name, "", url).trans();
    }

    public static Episode create(String name, String desc, String url) {
        return new Episode(name, desc, url).trans();
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return TextUtils.isEmpty(desc) ? "" : desc;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getNumber() {
        return number;
    }

    /** 统一集数展示：有集号的只显示两位编号（如 01），无集号的显示原始标题。 */
    public String getDisplay() {
        String text = (getDesc() + getName()).trim();
        int num = Util.getNumber(text);
        if (num > 0) return String.format(Locale.ROOT, "%02d", num);
        String stripped = text
                .replaceAll("^[【\\[]\\s*0*\\d{1,4}\\s*[】\\]]", "")
                .replaceAll("^[\\s\\-–—·:：,，、|。]+", "")
                .trim();
        return stripped.isEmpty() ? text : stripped;
    }

    /** 用于详情页当前集标题：纯集号返回"第N集"，带标题返回"第N集 标题"。 */
    public String getDisplayTitle() {
        return display(getName(), true);
    }

    public static String displayOf(String text, boolean verbose) {
        return new Episode().display(text, verbose);
    }

    private String display(String text, boolean verbose) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return "";
        int num = Util.getNumber(trimmed);
        String stripped = trimmed
                .replaceAll("[【\\[]\\s*(?i:ep?|第)?\\s*0*\\d{1,4}\\s*(?i:集|话|期)?\\s*[】\\]]", "")
                .replaceAll("(?i)(?:EP?|第)\\s*0*\\d{1,4}\\s*(?i:集|话|期)?", "")
                .replaceAll("^[\\s\\-–—·:：,，、|。]+|[\\s\\-–—·:：,，、|。]+$", "")
                .trim();
        if (num <= 0) return stripped.isEmpty() ? trimmed : trimmed.replaceAll("^0*\\d{1,4}(?i:集|话|期)?[\\s\\.·:：]+", "");
        String label = String.format(Locale.ROOT, "%02d", num);
        if (stripped.isEmpty()) return verbose ? "第" + num + "集" : label;
        return verbose ? "第" + num + "集 " + stripped : label + " " + stripped;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void deselect() {
        setSelected(false);
    }

    public int getScore(String name, int number) {
        if (getName().equalsIgnoreCase(name)) return 100;
        if (number != -1 && getNumber() == number) return 80;
        if (number == -1 && name.length() >= 2 && getName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) return 70;
        if (number == -1 && getName().length() >= 2 && name.toLowerCase(Locale.ROOT).contains(getName().toLowerCase(Locale.ROOT))) return 60;
        return 0;
    }

    public boolean matchesName(Episode other) {
        if (other == null) return false;
        return getName().equalsIgnoreCase(other.getName());
    }

    public Episode trans() {
        if (Trans.pass()) return this;
        this.name = Trans.s2t(name);
        this.desc = Trans.s2t(desc);
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Episode it)) return false;
        return Objects.equals(getName(), it.getName()) && Objects.equals(getUrl(), it.getUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getUrl());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeString(this.desc);
        dest.writeString(this.url);
        dest.writeInt(this.number);
        dest.writeByte(this.selected ? (byte) 1 : (byte) 0);
    }

    @Override
    public boolean isSameItem(Episode other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Episode other) {
        return getUrl().equals(other.getUrl()) && getDesc().equals(other.getDesc());
    }

    public record Rule(Episode episode, int score) {

        public boolean find() {
            return score > 0;
        }
    }

    public static final Creator<Episode> CREATOR = new Creator<>() {
        @Override
        public Episode createFromParcel(Parcel source) {
            return new Episode(source);
        }

        @Override
        public Episode[] newArray(int size) {
            return new Episode[size];
        }
    };
}
