<script setup lang="ts">
import { computed } from 'vue'
import { evaluatePassword } from '../utils/password'

const props = withDefaults(
  defineProps<{
    password: string
    showRequirements?: boolean
  }>(),
  {
    showRequirements: true,
  },
)

const strength = computed(() => evaluatePassword(props.password))

const progressColor = computed(() => {
  if (props.password.length === 0) {
    return '#c0c4cc'
  }
  if (strength.value.percent <= 50) {
    return '#f56c6c'
  }
  if (strength.value.percent === 75) {
    return '#e6a23c'
  }
  return '#67c23a'
})
</script>

<template>
  <div class="password-strength">
    <div class="password-strength__head">
      <span>密码强度</span>
      <span class="password-strength__label">{{ strength.label }}</span>
    </div>
    <el-progress
      :percentage="strength.percent"
      :color="progressColor"
      :show-text="false"
      :stroke-width="6"
    />
    <ul v-if="showRequirements" class="password-rules">
      <li :class="{ 'is-ok': strength.lengthOk }">至少 8 位</li>
      <li :class="{ 'is-ok': strength.upperOk }">包含大写字母</li>
      <li :class="{ 'is-ok': strength.lowerOk }">包含小写字母</li>
      <li :class="{ 'is-ok': strength.digitOk }">包含数字</li>
    </ul>
  </div>
</template>
