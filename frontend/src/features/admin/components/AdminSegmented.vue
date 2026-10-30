<script setup>
defineProps({
	label: {
		type: String,
		required: true,
	},
	modelValue: {
		type: [String, Number, Boolean, null],
		default: '',
	},
	// [{ value, label }]
	options: {
		type: Array,
		required: true,
	},
	hideLabel: {
		type: Boolean,
		default: false,
	},
});

defineEmits(['update:modelValue']);
</script>

<template>
	<div class="admin-field">
		<span v-if="!hideLabel" class="admin-field__label">{{ label }}</span>
		<div class="admin-segmented" role="group" :aria-label="label">
			<button
				v-for="option in options"
				:key="String(option.value)"
				class="admin-segmented__button"
				:class="{ 'admin-segmented__button--active': modelValue === option.value }"
				type="button"
				:aria-pressed="modelValue === option.value"
				@click="$emit('update:modelValue', option.value)"
			>
				{{ option.label }}
			</button>
		</div>
	</div>
</template>
