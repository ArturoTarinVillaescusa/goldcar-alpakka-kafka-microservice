export DOCKER_TLS_VERIFY=1
export COMPOSE_TLS_VERSION=TLSv1_2
export DOCKER_CERT_PATH=$PWD
export DOCKER_HOST=tcp://a286788e-4e3f-4d3c-954d-169c36d5001d.tcp.dockertrial.com:443

if kubectl >/dev/null 2>&1; then
    unset KUBECONFIG
    kubectl config set-cluster ucp_a286788e-4e3f-4d3c-954d-169c36d5001d.tcp.dockertrial.com:6443_arturo --server https://a286788e-4e3f-4d3c-954d-169c36d5001d.tcp.dockertrial.com:6443 --certificate-authority "$PWD/ca.pem" --embed-certs
    kubectl config set-credentials ucp_a286788e-4e3f-4d3c-954d-169c36d5001d.tcp.dockertrial.com:6443_arturo --client-key "$PWD/key.pem" --client-certificate "$PWD/cert.pem" --embed-certs
    kubectl config set-context ucp_a286788e-4e3f-4d3c-954d-169c36d5001d.tcp.dockertrial.com:6443_arturo --user ucp_a286788e-4e3f-4d3c-954d-169c36d5001d.tcp.dockertrial.com:6443_arturo --cluster ucp_a286788e-4e3f-4d3c-954d-169c36d5001d.tcp.dockertrial.com:6443_arturo
fi
export KUBECONFIG=$PWD/kube.yml

#
# Bundle for user arturo
# UCP Instance ID rdaetooystpqyvbt2yl7kciyj
#
# This admin cert will also work directly against Swarm and the individual
# engine proxies for troubleshooting.  After sourcing this env file, use
# "docker info" to discover the location of Swarm managers and engines.
# and use the --host option to override $DOCKER_HOST
#
# Run this command from within this directory to configure your shell:
# eval "$(<env.sh)"
