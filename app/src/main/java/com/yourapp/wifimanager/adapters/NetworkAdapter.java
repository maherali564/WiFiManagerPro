package com.yourapp.wifimanager.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yourapp.wifimanager.R;
import com.yourapp.wifimanager.models.WifiNetwork;

import java.util.List;

public class NetworkAdapter extends ArrayAdapter<WifiNetwork> {
    private Context context;
    private List<WifiNetwork> networks;

    public NetworkAdapter(@NonNull Context context, @NonNull List<WifiNetwork> networks) {
        super(context, R.layout.item_network, networks);
        this.context = context;
        this.networks = networks;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_network, parent, false);
        }

        WifiNetwork network = networks.get(position);

        TextView tvName = convertView.findViewById(R.id.tvNetworkName);
        TextView tvStatus = convertView.findViewById(R.id.tvNetworkStatus);
        TextView tvSecurity = convertView.findViewById(R.id.tvNetworkSecurity);

        tvName.setText(network.getSsid());
        tvSecurity.setText(network.isSecured() ? "🔒" : "🔓");

        String status = "";
        if (network.isConnected()) status += " 📶";
        if (network.isSaved()) status += " ⭐";
        tvStatus.setText(status);

        return convertView;
    }
}
