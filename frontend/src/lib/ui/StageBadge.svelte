<script lang="ts">
  import { stageLabel } from '../stages'
  let { stage }: { stage: string } = $props()
</script>

<span class="badge-stage" data-stage={stage}>{stageLabel(stage)}</span>
