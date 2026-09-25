# Infrastructure

Current development/staging environment: OVHcloud host `vps-32bea5b6`, Ubuntu 24.04 LTS, 4 vCores, approximately 8 GB RAM and approximately 72 GB root filesystem.

The previous DigitalOcean 1 vCPU / 1 GB RAM / 25 GB host is historical and is not a current operational dependency or development constraint.

The current PINK backend must remain on its approved local bind/configuration unless a specific deployment/network order authorizes a change.

Do not install WireGuard or change firewall/routing until a dedicated Supervisor order requires:

- snapshot/rollback;
- current service audit;
- selected UDP port;
- IP forwarding/NAT plan;
- rollback commands;
- bandwidth measurement plan.

Production design separates backend/control plane from a high-bandwidth VPN gateway when measured capacity and isolation requirements justify it. No concurrency/stream capacity is inferred from the current OVH hardware without benchmark evidence.
