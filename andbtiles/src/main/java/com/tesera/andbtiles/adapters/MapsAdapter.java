package com.tesera.andbtiles.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.tesera.andbtiles.R;
import com.tesera.andbtiles.pojos.MapItem;

import java.util.List;


public class MapsAdapter extends BaseAdapter {

    private Context mContext;
    private List<MapItem> mMaps;

    public MapsAdapter(Context mContext, List<MapItem> mMaps) {
        this.mContext = mContext;
        this.mMaps = mMaps;
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

        holder.name.setText(map.getName());
        holder.path.setText(map.getPath());

        return convertView;
    }

    static class ViewHolder {
        TextView name;
        TextView path;
    }
}
