export function canEnterAdmin(permissions:string[], required:string){return permissions.includes(required)||permissions.includes('system:config')}
