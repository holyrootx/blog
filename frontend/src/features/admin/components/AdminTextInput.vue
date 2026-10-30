<script setup>
import { useId } from 'vue';

const inputId = useId();

defineProps({
	label: {
		type: String,
		required: true,
	},
	modelValue: {
		type: String,
		default: '',
	},
	placeholder: {
		type: String,
		default: '',
	},
});

defineEmits(['update:modelValue']);
</script>

<template>
	<div class="admin-field">
		<label :for="inputId" class="admin-field__label">{{ label }}</label>
		<input
			:id="inputId"
			class="admin-field__input"
			type="text"
			:value="modelValue"
			:placeholder="placeholder"
			@input="$emit('update:modelValue', $event.target.value)"
		/>
	</div>
</template>
