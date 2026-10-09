import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import Home from './index';

describe('Home', () => {
  it('渲染就绪提示', () => {
    render(<Home />);

    expect(screen.getByText('平台门户已就绪')).toBeInTheDocument();
  });
});
