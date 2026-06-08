import { onBeforeUnmount } from 'vue';
import type { ECharts } from 'echarts';

/**
 * 统一管理一组 echarts 实例的尺寸自适应与生命周期。
 *
 * 用 ResizeObserver 监听每个图表容器的尺寸变化，因此窗口缩放、侧边栏折叠、
 * 布局变化都会触发 resize，无需手动监听 window.resize。组件卸载时自动断开
 * 观察并 dispose 图表，避免内存泄漏（现有 Dashboard 此前缺少 dispose）。
 *
 * 用法：
 *   const { register } = useChartAutoResize();
 *   const chart = echarts.init(el);
 *   register(chart, el);
 */
export function useChartAutoResize() {
  const charts: ECharts[] = [];
  const observers: ResizeObserver[] = [];

  function register(chart: ECharts, el: HTMLElement) {
    charts.push(chart);
    // requestAnimationFrame 合并同一帧内的多次尺寸变化，避免折叠动画期间频繁重绘
    let frame = 0;
    const ro = new ResizeObserver(() => {
      if (frame) cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => chart.resize());
    });
    ro.observe(el);
    observers.push(ro);
  }

  function disposeAll() {
    observers.forEach((o) => o.disconnect());
    charts.forEach((c) => c.dispose());
    observers.length = 0;
    charts.length = 0;
  }

  onBeforeUnmount(disposeAll);

  return { register, disposeAll };
}
