# Infrastructure

POC environment: existing DigitalOcean Ubuntu Droplet.

Do not install WireGuard or change firewall/routing until a dedicated supervisor order requires:
- snapshot/rollback
- current service audit
- selected UDP port
- IP forwarding/NAT plan
- rollback commands
- bandwidth measurement plan

Production design separates backend/control plane from high-bandwidth VPN gateway.
