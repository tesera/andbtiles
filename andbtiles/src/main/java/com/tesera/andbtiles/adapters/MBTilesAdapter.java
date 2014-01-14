package com.tesera.andbtiles.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.tesera.andbtiles.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class MBTilesAdapter extends BaseAdapter implements Filterable {

    private Context mContext;
    private List<File> mFiles;
    private List<File> mFilesOriginal;

    public MBTilesAdapter(Context mContext, List<File> mFiles) {
        this.mContext = mContext;
        this.mFiles = mFiles;
        this.mFilesOriginal = mFiles;
    }

    @Override
    public int getCount() {
        return mFiles.size();
    }

    @Override
    public File getItem(int position) {
        return mFiles.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_map, parent, false);
            // standard view holder pattern
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.name = (TextView) convertView.findViewById(R.id.txt_name);
            viewHolder.path = (TextView) convertView.findViewById(R.id.txt_path);
            convertView.setTag(viewHolder);
        }

        ViewHolder holder = (ViewHolder) convertView.getTag();
        File map = mFiles.get(position);

        holder.name.setText(map.getName());
        holder.path.setText(map.getAbsolutePath());

        return convertView;
    }


    @Override
    public Filter getFilter() {
        return new Filter() {

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, Filter.FilterResults results) {
                if (results.values != null) {
                    mFiles = (List<File>) results.values;
                    notifyDataSetChanged();
                } else
                    notifyDataSetInvalidated();
            }

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filteredResults = new FilterResults();
                filteredResults.values = getFilteredResults(constraint);
                return filteredResults;
            }

            private List<File> getFilteredResults(CharSequence constraint) {
                List<File> filteredList = new ArrayList<File>();
                // always use the original adapter list when filtering
                if (mFilesOriginal == null)
                    return null;
                if (constraint.length() == 0) {
                    mFiles = mFilesOriginal;
                    return mFiles;
                }

                for (File mall : mFilesOriginal) {
                    if (mall.getName().toLowerCase().contains(constraint.toString().toLowerCase()))
                        filteredList.add(mall);
                }
                return filteredList;
            }
        };
    }

    static class ViewHolder {
        TextView name;
        TextView path;
    }
}
