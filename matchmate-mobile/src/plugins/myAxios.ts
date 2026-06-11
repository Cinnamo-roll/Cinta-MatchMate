import axios from 'axios';

const myAxios = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
});

export default myAxios;
