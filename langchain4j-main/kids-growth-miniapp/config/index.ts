import { defineConfig, type UserConfigExport } from '@tarojs/cli';

export default defineConfig<'webpack5'>(async (merge, { command, mode }) => {
  const useCloudContainer = process.env.TARO_APP_USE_CLOUD_CONTAINER !== 'false';
  const apiBaseUrl = process.env.TARO_APP_API_BASE_URL
    || (useCloudContainer ? '' : 'http://127.0.0.1:8090');
  const baseConfig: UserConfigExport<'webpack5'> = {
    projectName: 'kids-growth-miniapp',
    date: '2026-08-14',
    designWidth: 750,
    deviceRatio: {
      640: 2.34 / 2,
      750: 1,
      828: 1.81 / 2
    },
    sourceRoot: 'src',
    outputRoot: 'dist',
    framework: 'react',
    compiler: 'webpack5',
    defineConstants: {
      __API_BASE_URL__: JSON.stringify(apiBaseUrl),
      __CLOUD_ENV_ID__: JSON.stringify(process.env.TARO_APP_CLOUD_ENV_ID || 'prod-d8g1em4boece7c8ea'),
      __CLOUD_SERVICE_NAME__: JSON.stringify(process.env.TARO_APP_CLOUD_SERVICE_NAME || 'kids-growth-api'),
      __USE_CLOUD_CONTAINER__: JSON.stringify(useCloudContainer)
    },
    cache: {
      enable: false
    },
    mini: {
      postcss: {
        pxtransform: {
          enable: true,
          config: {}
        },
        url: {
          enable: true,
          config: {
            limit: 1024
          }
        },
        cssModules: {
          enable: false,
          config: {
            namingPattern: 'module',
            generateScopedName: '[name]__[local]___[hash:base64:5]'
          }
        }
      }
    }
  };

  if (process.env.NODE_ENV === 'development') {
    return merge({}, baseConfig, {
      logger: {
        quiet: false,
        stats: true
      }
    });
  }
  return merge({}, baseConfig, {});
});
