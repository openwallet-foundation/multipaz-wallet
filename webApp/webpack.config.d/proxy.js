if (config.devServer) {
    config.devServer.port = 8011;
    config.devServer.proxy = [
        {
            context: ['/rpc', '/push'],
            target: 'http://localhost:8010',
        },
    ];
}
