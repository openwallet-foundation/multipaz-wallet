if (config.devServer) {
    config.devServer.port = 8011;
    config.devServer.historyApiFallback = {
        rewrites: [
            { from: /^\/web\/verify/, to: '/verify.html' },
            { from: /^\/web\/keys/, to: '/keys.html' },
            { from: /^\/web\/privacy/, to: '/docs.html' },
            { from: /^\/web\/terms/, to: '/docs.html' },
            { from: /^\/web\/google-privacy/, to: '/docs.html' },
            { from: /^\/web\/google-terms/, to: '/docs.html' },
            { from: /^\/web\/webApp\.js/, to: '/webApp.js' },
            { from: /^\/verify/, to: '/verify.html' },
            { from: /^\/keys/, to: '/keys.html' },
            { from: /^\/privacy/, to: '/docs.html' },
            { from: /^\/terms/, to: '/docs.html' },
            { from: /^\/google-privacy/, to: '/docs.html' },
            { from: /^\/google-terms/, to: '/docs.html' }
        ]
    };
    config.devServer.proxy = [
        {
            context: ['/rpc', '/push', '/api'],
            target: 'http://localhost:8010',
        },
    ];
}
