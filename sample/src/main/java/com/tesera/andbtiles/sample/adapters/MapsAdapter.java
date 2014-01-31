package com.tesera.andbtiles.sample.adapters;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.tesera.andbtiles.pojos.MapItem;
import com.tesera.andbtiles.sample.R;
import com.tesera.andbtiles.sample.utils.Consts;

import java.util.ArrayList;
import java.util.List;


public class MapsAdapter extends BaseAdapter implements Filterable {

    private Context mContext;
    private List<MapItem> mMaps;
    private List<MapItem> mMapsOriginal;

    public MapsAdapter(Context mContext, List<MapItem> mMaps) {
        this.mContext = mContext;
        this.mMaps = mMaps;
        this.mMapsOriginal = mMaps;
    }

    @Override
    public int getCount() {
        return mMaps.size();
    }

    @Override
    public MapItem getItem(int position) {
        return mMaps.get(position);
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
        MapItem map = mMaps.get(position);

        holder.name.setText(map.getName().replace("." + Consts.EXTENSION_MBTILES, ""));
        holder.path.setText(map.getPath());
        if (map.getSize() != 0)
            holder.path.append("\n" + Formatter.formatFileSize(mContext, map.getSize()));

        return convertView;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.values != null) {
                    mMaps = (List<MapItem>) results.values;
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

            private List<MapItem> getFilteredResults(CharSequence constraint) {
                List<MapItem> filteredList = new ArrayList<MapItem>();
                // always use the original adapter list when filtering
                if (mMapsOriginal == null)
                    return null;
                if (constraint.length() == 0) {
                    mMaps = mMapsOriginal;
                    return mMaps;
                }

                for (MapItem mapItem : mMapsOriginal) {
                    if (mapItem.getName().toLowerCase().contains(constraint.toString().toLowerCase()))
                        filteredList.add(mapItem);
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
