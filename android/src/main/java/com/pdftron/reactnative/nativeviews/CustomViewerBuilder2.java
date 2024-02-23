package com.pdftron.reactnative.nativeviews;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pdftron.pdf.config.BaseViewerBuilderImpl;
import com.pdftron.pdf.config.ViewerConfig;
import com.pdftron.pdf.interfaces.builder.SkeletalFragmentBuilder;

import org.json.JSONObject;

import java.io.File;
import java.util.Objects;

public class CustomViewerBuilder2 extends SkeletalFragmentBuilder<CustomPdfViewCtrlTabHostFragment> implements Parcelable {
    @NonNull
    private final ViewerBuilderImpl mImpl;

    public static final Creator<CustomViewerBuilder2> CREATOR = new Creator<CustomViewerBuilder2>() {
        public CustomViewerBuilder2 createFromParcel(Parcel source) {
            return new CustomViewerBuilder2(source);
        }

        public CustomViewerBuilder2[] newArray(int size) {
            return new CustomViewerBuilder2[size];
        }
    };

    private CustomViewerBuilder2() {
        mImpl = new ViewerBuilderImpl();
    }

    @Override
    public Bundle createBundle(@NonNull Context context) {
        return null;
    }

    public void checkArgs(@NonNull Context context) {}

    @SuppressLint("RestrictedApi")
    public static CustomViewerBuilder2 withUri(@Nullable Uri file, @Nullable String password) {
        CustomViewerBuilder2 builder = new CustomViewerBuilder2();
        builder.mImpl.withUri(file, password);
        return builder;
    }

    public static CustomViewerBuilder2 withUri(@Nullable Uri file) {
        return withUri(file, null);
    }

    public static CustomViewerBuilder2 withFile(@Nullable File file, @Nullable String password) {
        return withUri(file != null ? Uri.fromFile(file) : null, password);
    }

    public static CustomViewerBuilder2 withFile(@Nullable File file) {
        return withUri(file != null ? Uri.fromFile(file) : null, (String)null);
    }

    @SuppressLint("RestrictedApi")
    public CustomPdfViewCtrlTabHostFragment build(@NonNull Context context) {
        return this.mImpl.build(context);
    }

    @SuppressLint("RestrictedApi")
    public CustomViewerBuilder2 usingConfig(@NonNull ViewerConfig config) {
        this.mImpl.usingConfig(config);
        return this;
    }

    @SuppressLint("RestrictedApi")
    public CustomViewerBuilder2 usingNavIcon(@DrawableRes int navIconRes) {
        this.mImpl.usingNavIcon(navIconRes);
        return this;
    }

    @SuppressLint("RestrictedApi")
    public CustomViewerBuilder2 usingCustomHeaders(@Nullable JSONObject headers) {
        this.mImpl.usingCustomHeaders(headers);
        return this;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.mImpl, flags);
    }

    protected CustomViewerBuilder2(Parcel in) {
        super();
        this.mImpl = Objects.requireNonNull(in.readParcelable(ViewerBuilderImpl.class.getClassLoader()));
    }

    @SuppressLint("RestrictedApi")
    private static class ViewerBuilderImpl extends BaseViewerBuilderImpl<CustomPdfViewCtrlTabHostFragment, RNPdfViewCtrlTabFragment> {
        public static final Creator<ViewerBuilderImpl> CREATOR = new Creator<ViewerBuilderImpl>() {
            public ViewerBuilderImpl createFromParcel(Parcel source) {
                return new ViewerBuilderImpl(source);
            }

            public ViewerBuilderImpl[] newArray(int size) {
                return new ViewerBuilderImpl[size];
            }
        };

        ViewerBuilderImpl() {
        }

        protected ViewerBuilderImpl(Parcel in) {
            super(in);
        }

        @NonNull
        @Override
        protected Class<RNPdfViewCtrlTabFragment> useDefaultTabFragmentClass() {
            return RNPdfViewCtrlTabFragment.class;
        }

        @NonNull
        @Override
        protected Class<CustomPdfViewCtrlTabHostFragment> useDefaultTabHostFragmentClass() {
            return CustomPdfViewCtrlTabHostFragment.class;
        }

        @NonNull
        @Override
        protected BaseViewerBuilderImpl useBuilder() {
            return this;
        }
    }
}
