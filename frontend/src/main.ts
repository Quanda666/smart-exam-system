/**
 * 智慧在线考试系统 - 前端主入口
 *
 * 课程：Web程序设计课程设计（S3048I）
 * 组别：第二组
 * 项目：在线考试系统
 */
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import './styles.css';
import App from './App.vue';
import { router } from './router';

createApp(App).use(router).use(ElementPlus).mount('#app');
