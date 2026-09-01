import { Component, type ErrorInfo, type PropsWithChildren } from 'react';
import { initializeCloudContainer } from './services/api-client';
import './app.scss';

class App extends Component<PropsWithChildren> {
  componentDidMount() {
    // Cloud initialization is lazy. Guest DevTools cannot call operateWXData,
    // while real requests still initialize the container in api-client.
    return;
    initializeCloudContainer().catch((error) => {
      console.error('[语芽云托管初始化失败]', error);
    });
  }

  onError(error: string) {
    console.error('[语芽小程序运行异常]', error);
  }

  onUnhandledRejection({ reason }: { reason: unknown }) {
    console.error('[语芽小程序未处理的异步异常]', reason);
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[语芽 React 渲染异常]', error, info.componentStack);
  }

  render() {
    return this.props.children;
  }
}

export default App;
