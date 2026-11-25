CREATE TABLE IF NOT EXISTS cloudflare_accounts (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    global_api_key VARCHAR(255) NOT NULL,
    account_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dns_records (
    id UUID PRIMARY KEY,
    account_id UUID REFERENCES cloudflare_accounts(id) ON DELETE CASCADE,
    zone_id VARCHAR(255) NOT NULL,
    zone_name VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    content VARCHAR(255) NOT NULL,
    ttl INT NOT NULL DEFAULT 1,
    proxied BOOLEAN NOT NULL DEFAULT false,
    auto_sync BOOLEAN NOT NULL DEFAULT false,
    cf_record_id VARCHAR(255),
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS edge_tunnels (
    id UUID PRIMARY KEY,
    account_id UUID REFERENCES cloudflare_accounts(id) ON DELETE CASCADE,
    tunnel_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    config_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dns_records_account_id ON dns_records(account_id);
CREATE INDEX IF NOT EXISTS idx_dns_records_zone_id ON dns_records(zone_id);
CREATE INDEX IF NOT EXISTS idx_edge_tunnels_account_id ON edge_tunnels(account_id);
CREATE INDEX IF NOT EXISTS idx_edge_tunnels_tunnel_id ON edge_tunnels(tunnel_id);
