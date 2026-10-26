<script setup lang="ts">
import { rateLimitState } from '../stores/rate-limit'
</script>

<template>
  <div v-if="rateLimitState.seconds > 0" class="rate-limit-notice">
    <el-alert type="warning" :closable="false" show-icon>
      <template #title>
        {{ rateLimitState.message }}，请
        <strong class="rate-limit-notice__count">{{ rateLimitState.seconds }}</strong>
        秒后重试
      </template>
    </el-alert>
  </div>
</template>
