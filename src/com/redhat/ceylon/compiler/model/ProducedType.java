package com.redhat.ceylon.compiler.model;

import java.util.List;
import java.util.Map;


public class ProducedType extends ProducedReference {
	
    ProducedType() {}
    
	@Override
    public TypeDeclaration getDeclaration() {
		return (TypeDeclaration) super.getDeclaration();
	}
		
	@Override
	public String toString() {
		return "Type[" + getProducedTypeName() + "]";
	}

	public String getProducedTypeName() {
        if (getDeclaration() == null) {
            //unknown type
            return null;
        }
		String producedTypeName = getDeclaration().getName();
		if (!getTypeArguments().isEmpty()) {
			producedTypeName+="<";
			for (TypeParameter p: getDeclaration().getTypeParameters()) {
			    ProducedType t = getTypeArguments().get(p);
			    if (t==null) {
			        producedTypeName+="null";
			    }
			    else {
			        producedTypeName+=t.getProducedTypeName() + ",";
			    }
			}
			producedTypeName+=">";
		}
		return producedTypeName.replace(",>", ">");
	}
	
	public boolean isExactly(ProducedType that) {
	    if (that.getDeclaration()!=getDeclaration()) {
	        return false;
	    }
        for (TypeParameter p: getDeclaration().getTypeParameters()) {
	        if ( !that.getTypeArguments().get(p).isExactly(getTypeArguments().get(p)) ) {
	            return false;
	        }
	    }
	    return true;
	}
	
    ProducedType substitute(Map<TypeParameter,ProducedType> substitutions) {
        if (getDeclaration() instanceof TypeParameter) {
            ProducedType sub = substitutions.get(getDeclaration());
            if (sub!=null) return sub;
        }
        ProducedType t = new ProducedType();
        t.setDeclaration(getDeclaration());
        t.setTypeArguments(sub(substitutions));
        return t;
    }
    
    private Map<TypeParameter,ProducedType> memberArgs(Declaration d, List<ProducedType> typeArguments) {
        Map<TypeParameter, ProducedType> map = Util.arguments(d, typeArguments);
        map.putAll(sub(map));
        return map;
    }
    
    public ProducedTypedReference getTypedMember(TypedDeclaration td, List<ProducedType> typeArguments) {
        if (!Util.acceptsArguments(td, typeArguments)) {
            return null;
        }
        ProducedTypedReference ptr = new ProducedTypedReference();
        ptr.setDeclaration(td);
        ptr.setDeclaringType(this);
        ptr.setTypeArguments(memberArgs(td, typeArguments));
        return ptr;
    }
         
    public ProducedType getTypeMember(TypeDeclaration td, List<ProducedType> typeArguments) {
        if (!Util.acceptsArguments(td, typeArguments)) {
            return null;
        }
        ProducedType pt = new ProducedType();
        pt.setDeclaration(td);
        pt.setDeclaringType(this);
        pt.setTypeArguments(memberArgs(td, typeArguments));
        return pt;
    }
    
    public ProducedType getType() {
        return this;
    }
    
}
