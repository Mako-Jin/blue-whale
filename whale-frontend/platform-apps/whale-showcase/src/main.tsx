import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

const mountNode = document.getElementById('root');
if (mountNode) {
  ReactDOM.createRoot(mountNode).render(<App />);
}
