import { useEffect } from 'react';
import { useRoutes } from 'react-router-dom';
import { routes } from './router/routes';
import { initQiankun } from './qiankun';

export default function App() {
  const element = useRoutes(routes);

  useEffect(() => {
    initQiankun();
  }, []);

  return element;
}
