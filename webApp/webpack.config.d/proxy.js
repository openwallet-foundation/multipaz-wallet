if (config.devServer) {
    config.devServer.port = 8011;
    config.devServer.historyApiFallback = {
        rewrites: [
            { from: /^\/web\/verify/, to: '/verify.html' },
            { from: /^\/web\/keys/, to: '/keys.html' },
            { from: /^\/web\/webApp\.js/, to: '/webApp.js' },
            { from: /^\/verify/, to: '/verify.html' },
            { from: /^\/keys/, to: '/keys.html' }
        ]
    };
    config.devServer.proxy = [
        {
            context: ['/rpc', '/push', '/api'],
            target: 'http://localhost:8010',
        },
    ];
}
