import { ref } from 'vue';

const sidebarCollapsed = ref(localStorage.getItem('pref_sidebar_collapsed') === '1');

export function useSidebarCollapsed() {
  return sidebarCollapsed;
}
