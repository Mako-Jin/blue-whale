import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Home from './index';
import { MICRO_APPS } from '@/micro';

describe('Home', () => {
  it('渲染就绪提示', () => {
    render(
      <MemoryRouter>
        <Home />
      </MemoryRouter>,
    );

    expect(screen.getByText('平台门户已就绪')).toBeInTheDocument();
  });

  it('列出注册表里的全部子应用入口', () => {
    render(
      <MemoryRouter>
        <Home />
      </MemoryRouter>,
    );

    for (const app of MICRO_APPS) {
      expect(screen.getByText(app.title)).toBeInTheDocument();
      expect(screen.getByText(app.name)).toBeInTheDocument();
    }
  });
});
