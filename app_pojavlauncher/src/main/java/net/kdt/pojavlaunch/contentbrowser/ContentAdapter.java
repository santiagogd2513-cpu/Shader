package net.kdt.pojavlaunch.contentbrowser;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** List of search results in the content browser */
public class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.Holder> {
    public interface Listener {
        void onDownloadClicked(ModrinthClient.Project project);
    }

    private final List<ModrinthClient.Project> mProjects = new ArrayList<>();
    private final ModIconCache mIconCache = new ModIconCache();
    private final Listener mListener;

    public ContentAdapter(Listener listener) {
        mListener = listener;
    }

    public void clear() {
        int size = mProjects.size();
        mProjects.clear();
        notifyItemRangeRemoved(0, size);
    }

    public void addAll(List<ModrinthClient.Project> projects) {
        int start = mProjects.size();
        mProjects.addAll(projects);
        notifyItemRangeInserted(start, projects.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content_project, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(mProjects.get(position));
    }

    @Override
    public int getItemCount() {
        return mProjects.size();
    }

    static String formatDownloads(long downloads) {
        if(downloads >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", downloads / 1_000_000f);
        if(downloads >= 1_000) return String.format(Locale.ROOT, "%.1fK", downloads / 1_000f);
        return String.valueOf(downloads);
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mTitle;
        private final TextView mMeta;
        private final TextView mDescription;
        private final Button mDownload;
        private ImageReceiver mImageReceiver;
        private String mBoundTag;

        Holder(@NonNull View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.content_item_icon);
            mTitle = itemView.findViewById(R.id.content_item_title);
            mMeta = itemView.findViewById(R.id.content_item_meta);
            mDescription = itemView.findViewById(R.id.content_item_description);
            mDownload = itemView.findViewById(R.id.content_item_download);
        }

        void bind(ModrinthClient.Project project) {
            mTitle.setText(project.title);
            String author = project.author == null ? "" : project.author;
            mMeta.setText(itemView.getContext().getString(R.string.content_item_meta,
                    author, formatDownloads(project.downloads)));
            mDescription.setText(project.description);
            mDownload.setOnClickListener(v -> mListener.onDownloadClicked(project));

            // Icon (cached on disk, loaded in the background)
            if(mImageReceiver != null) mIconCache.cancelImage(mImageReceiver);
            mIcon.setImageResource(R.mipmap.ic_launcher_foreground);
            mBoundTag = project.getIconTag();
            if(project.iconUrl == null || project.iconUrl.isEmpty()) {
                mImageReceiver = null;
                return;
            }
            final String tag = mBoundTag;
            mImageReceiver = new ImageReceiver() {
                @Override
                public void onImageAvailable(Bitmap image) {
                    mIcon.post(() -> {
                        if(image != null && tag.equals(mBoundTag)) mIcon.setImageBitmap(image);
                    });
                }
            };
            mIconCache.getImage(mImageReceiver, tag, project.iconUrl);
        }
    }
}
