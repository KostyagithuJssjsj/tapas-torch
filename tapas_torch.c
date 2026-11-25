#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/kprobes.h>
#include <linux/ptrace.h>

typedef unsigned long (*kallsyms_lookup_name_t)(const char *name);

static int *p_led_flash_state;
static u32 *p_flash_open_array;
static u32 *p_flash_init_array;
static u32 orig_open_cur = 0x2c;
static u32 orig_init_cur = 0x2c;

static int __kprobes torch_pre_handler(struct kprobe *p, struct pt_regs *regs)
{
	unsigned int val = (unsigned int)regs->regs[1];

	if (val > 0) {
		u32 cur = val >> 2; /* 1..255 -> 0..63 (0x00..0x3F, safe KTD2691 torch range) */
		if (cur > 0x3f)
			cur = 0x3f;
		p_flash_open_array[1] = cur;
		p_flash_init_array[9] = cur;
		if (*p_led_flash_state != (int)val)
			*p_led_flash_state = 0;
	}
	return 0;
}

static struct kprobe kp_torch = {
	.symbol_name = "torch_brightness_set",
	.pre_handler = torch_pre_handler,
};

static __nocfi unsigned long lookup_sym(kallsyms_lookup_name_t fn, const char *name)
{
	return fn(name);
}

static int __init tapas_torch_init(void)
{
	struct kprobe kp_ks = { .symbol_name = "kallsyms_lookup_name" };
	kallsyms_lookup_name_t fn;
	int ret;

	ret = register_kprobe(&kp_ks);
	if (ret < 0) {
		pr_err("tapas_torch: failed to find kallsyms_lookup_name (%d)\n", ret);
		return ret;
	}
	fn = (kallsyms_lookup_name_t)kp_ks.addr;
	unregister_kprobe(&kp_ks);

	p_led_flash_state  = (int *)lookup_sym(fn, "led_flash_state");
	p_flash_open_array = (u32 *)lookup_sym(fn, "flash_open_array");
	p_flash_init_array = (u32 *)lookup_sym(fn, "flash_init_array");

	if (!p_led_flash_state || !p_flash_open_array || !p_flash_init_array) {
		pr_err("tapas_torch: missing camera.ko flash symbols\n");
		return -ENOENT;
	}

	if (p_flash_open_array[0] != 0x04 || p_flash_init_array[8] != 0x04) {
		pr_err("tapas_torch: unexpected KTD2691 reg layout (0x%x / 0x%x)\n",
		       p_flash_open_array[0], p_flash_init_array[8]);
		return -EINVAL;
	}

	orig_open_cur = p_flash_open_array[1];
	orig_init_cur = p_flash_init_array[9];

	ret = register_kprobe(&kp_torch);
	if (ret < 0) {
		pr_err("tapas_torch: register_kprobe(torch_brightness_set) failed (%d)\n", ret);
		return ret;
	}

	pr_info("tapas_torch: ready! Smooth 64-step KTD2691 torch control active (orig=0x%x)\n",
		orig_open_cur);
	return 0;
}

static void __exit tapas_torch_exit(void)
{
	unregister_kprobe(&kp_torch);
	if (p_flash_open_array)
		p_flash_open_array[1] = orig_open_cur;
	if (p_flash_init_array)
		p_flash_init_array[9] = orig_init_cur;
	pr_info("tapas_torch: unloaded, restored stock brightness 0x%x\n", orig_open_cur);
}

module_init(tapas_torch_init);
module_exit(tapas_torch_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("Kostya");
MODULE_DESCRIPTION("Smooth hardware torch brightness control for Redmi Note 12 4G (KTD2691)");
